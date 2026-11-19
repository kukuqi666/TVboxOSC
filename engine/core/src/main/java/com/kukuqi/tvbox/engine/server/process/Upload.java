package com.kukuqi.tvbox.engine.server.process;

import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Path;
import com.kukuqi.tvbox.engine.server.Nano;
import com.kukuqi.tvbox.engine.server.impl.Process;
import com.kukuqi.tvbox.engine.utils.FileUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Resumable chunked upload behind {@code /upload/start}, {@code /upload/chunk} and {@code /upload/finish}.
 *
 * <p>Parts are written straight into a staging file at their byte offset, so an interrupted transfer
 * resumes from whatever offset {@code start} reports. Only {@code finish} verifies the whole file
 * against the caller's SHA-256 and moves it into place, which keeps a half-written file out of the
 * target directory. Sequential and parallel mode share the same staging file; parallel mode adds a
 * {@code revision} so a caller can tell whether its chunk map is still current.</p>
 */
public class Upload implements Process {

    private static final String START = "/upload/start";
    private static final String CHUNK = "/upload/chunk";
    private static final String FINISH = "/upload/finish";
    private static final long CHUNK_SIZE = 4L * 1024 * 1024;
    private static final int THREADS = 4;
    /** A transfer nobody touches for this long is dropped, and {@code start} rebuilds it. */
    private static final long EXPIRE_MILLIS = 24L * 60 * 60 * 1000;
    private static final int MAX_SESSIONS = 8;
    private static final java.util.regex.Pattern ID_PATTERN = java.util.regex.Pattern.compile("[0-9a-f]{32}");
    private static final java.util.regex.Pattern SHA_PATTERN = java.util.regex.Pattern.compile("[0-9a-f]{64}");

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.equals(START) || url.equals(CHUNK) || url.equals(FINISH);
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            Map<String, String> params = merge(session.getParms(), files);
            if (url.equals(START)) return Nano.ok(start(params).toString());
            if (url.equals(CHUNK)) return Nano.ok(chunk(session, files).toString());
            return Nano.ok(finish(params).toString());
        } catch (NotFound e) {
            return Nano.error(Response.Status.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            return Nano.error(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private synchronized JsonObject start(Map<String, String> params) throws IOException {
        String id = requireId(params.get("id"));
        File directory = resolveDirectory(require(params, "path"));
        String name = requireName(require(params, "name"));
        long size = requireLong(params.get("size"), "size");
        if (size < 0) throw new IllegalArgumentException("size 不能为负数");
        String sha256 = requireSha(params.get("sha256"));
        boolean parallel = "true".equalsIgnoreCase(String.valueOf(params.get("parallel")).trim());

        Session session = sessions.get(id);
        if (session != null && !session.matches(directory, name, size, sha256)) {
            // 同一 id 换了档案：旧暂存已无意义，直接重建
            session.discard();
            session = null;
        }
        if (session == null || session.expired()) {
            if (session != null) session.discard();
            purgeIfFull();
            session = new Session(id, directory, name, size, sha256);
            sessions.put(id, session);
        }
        session.touch();
        return session.describe(parallel);
    }

    /** NanoHTTPD 的 multipart 解码不适用于数 MiB 的内容，分块直接取原始请求体。 */
    private synchronized JsonObject chunk(IHTTPSession session, Map<String, String> files) throws IOException, NotFound {
        Map<String, String> params = chunkParams(session, files);
        String id = requireId(params.get("id"));
        Session upload = require(id);
        long offset = requireLong(params.get("offset"), "offset");
        long size = requireLong(params.get("size"), "size");
        if (size <= 0) throw new IllegalArgumentException("size 必须大于 0");
        if (offset < 0) throw new IllegalArgumentException("offset 不能为负数");
        if (offset + size > upload.size) throw new IllegalArgumentException("分块超出档案大小");
        if (params.containsKey("revision") && requireLong(params.get("revision"), "revision") != upload.revision) return stale(upload);
        File source = payload(id, files);
        try {
            writeAt(upload.part(), source, offset, size);
            upload.markChunk(offset);
            upload.touch();
            return upload.describe(false);
        } finally {
            // NanoHTTPD 把 multipart 暂存在缓存目录，落盘后即可释放
            if (!source.delete() && source.exists()) source.deleteOnExit();
        }
    }

    private synchronized JsonObject finish(Map<String, String> params) throws IOException, NotFound {
        String id = requireId(params.get("id"));
        Session session = require(id);
        if (params.containsKey("revision") && requireLong(params.get("revision"), "revision") != session.revision) return stale(session);
        File part = session.part();
        if (!part.isFile() || part.length() != session.size) {
            session.touch();
            JsonObject result = session.describe(false);
            result.addProperty("complete", false);
            result.addProperty("error", "档案尚未完整");
            return result;
        }
        String actual = hex(Crypto.sha256(part));
        if (!actual.equalsIgnoreCase(session.sha256)) {
            // 内容已被破坏，重来比猜测哪一块出错更可靠
            session.discard();
            sessions.remove(id);
            JsonObject result = new JsonObject();
            result.addProperty("id", id);
            result.addProperty("complete", false);
            result.addProperty("error", "SHA-256 不符，请重新上传");
            return result;
        }
        store(part, session.directory, session.name);
        sessions.remove(id);
        JsonObject result = session.describe(false);
        result.addProperty("complete", true);
        return result;
    }

    /** 目标目录必须已存在；不存在就报错，而不是悄悄建出来。 */
    private static File resolveDirectory(String path) throws IOException {
        File directory = Path.local(path).getCanonicalFile();
        if (!directory.isDirectory()) throw new IllegalArgumentException("Upload directory not found");
        return directory;
    }

    private static void store(File part, File directory, String name) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            if (!FileUtil.zipDecompress(part, directory)) throw new IOException("Unable to extract archive");
            if (!part.delete()) part.deleteOnExit();
            return;
        }
        File target = new File(directory, name).getCanonicalFile();
        if (!target.getParentFile().equals(directory)) throw new SecurityException("Invalid file name");
        // 共享存储是 FUSE 挂载，建临时文件再改名会被拒，因此直接顺序写入目标
        try (java.io.InputStream in = new FileInputStream(part); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = in.read(buffer)) > 0; ) out.write(buffer, 0, read);
        }
        if (!part.delete()) part.deleteOnExit();
    }

    /**
     * 取分块内容。multipart 解码在大内容上不可靠，因此优先读原始请求体；只有在请求体已被
     * NanoHTTPD 落盘时才退回文件栏位。
     */
    private static File payload(String id, Map<String, String> files) throws IOException {
        // parseBody 走后，超过内存阈值的栏位以临时文件形式出现在 files 里，键名是栏位名或 postData+栏位名
        for (String key : files.keySet()) {
            String name = key.startsWith("postData") ? key.substring("postData".length()) : key;
            if (!name.equalsIgnoreCase("chunk")) continue;
            File stored = new File(files.get(key));
            if (stored.isFile() && stored.length() > 0) return stored;
        }
        throw new IllegalArgumentException("Missing upload chunk");
    }

    private static void writeAt(File part, File source, long offset, long size) throws IOException {
        try (RandomAccessFile out = new RandomAccessFile(part, "rw"); FileInputStream in = new FileInputStream(source)) {
            // 先截断到目标长度，保证稀疏写入后的长度恰好等于整体大小
            if (source.length() != size) throw new IllegalArgumentException("chunk 大小与 size 不符");
            out.seek(offset);
            byte[] buffer = new byte[8192];
            long written = 0;
            for (int read; (read = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, read);
                written += read;
            }
            if (written != size) throw new IOException("分块写入不完整");
        }
    }

    /** 分块索引由 offset 决定，供并行模式回报已收到的块。 */
    private JsonObject stale(Session session) {
        JsonObject result = session.describe(true);
        result.addProperty("complete", false);
        result.addProperty("error", "revision 已失效，请重新 start");
        return result;
    }

    private Session require(String id) throws NotFound {
        Session session = sessions.get(id);
        if (session == null || session.expired()) {
            if (session != null) {
                session.discard();
                sessions.remove(id);
            }
            throw new NotFound("上传已过期，请重新 start");
        }
        return session;
    }

    private void purgeIfFull() {
        if (sessions.size() < MAX_SESSIONS) return;
        sessions.values().stream().min((a, b) -> Long.compare(a.touched, b.touched)).ifPresent(old -> {
            old.discard();
            sessions.remove(old.id);
        });
    }

    /**
     * NanoHTTPD 把超过 {@code MEMORY_STORE_LIMIT} 的 multipart 字段写进临时文件，字段名就只剩在
     * {@code files} 的键里；这里把文件字段名补回参数表，免得大分块时 {@code id} 之类的短字段丢失。
     */
    /**
     * 分块元数据取值顺序：查询串 → 表单栏位。
     *
     * <p>NanoHTTPD 2.3.1 的 multipart 解码在数 MiB 的请求体上会失败，而 {@code Nano.parse} 会吞掉
     * 那个异常，结果表单栏位全部丢失。查询串不经过解码，因此大分块改用 {@code ?id=&offset=&size=}
     * 传参；请求体本身仍按原样写入暂存档。</p>
     */
    private static Map<String, String> chunkParams(IHTTPSession session, Map<String, String> files) {
        Map<String, String> result = new java.util.HashMap<>();
        String query = session.getQueryParameterString();
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                int split = pair.indexOf('=');
                if (split <= 0) continue;
                try {
                    result.put(java.net.URLDecoder.decode(pair.substring(0, split), "UTF-8"),
                            java.net.URLDecoder.decode(pair.substring(split + 1), "UTF-8"));
                } catch (java.io.UnsupportedEncodingException ignored) {
                }
            }
        }
        result.putAll(merge(session.getParms(), files));
        return result;
    }

    private static Map<String, String> merge(Map<String, String> parms, Map<String, String> files) {
        if (files.isEmpty()) return parms;
        Map<String, String> merged = new java.util.HashMap<>(parms);
        for (String key : files.keySet()) merged.putIfAbsent(key, files.get(key));
        return merged;
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) builder.append(String.format(Locale.ROOT, "%02x", value));
        return builder.toString();
    }

    private static String require(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static String requireId(String value) {
        String id = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(id).matches()) throw new IllegalArgumentException("id 需为 32 位小写十六进位");
        return id;
    }

    private static String requireSha(String value) {
        String sha = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!SHA_PATTERN.matcher(sha).matches()) throw new IllegalArgumentException("sha256 需为 64 位小写十六进位");
        return sha;
    }

    private static long requireLong(String value, String key) {
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " 需为整数");
        }
    }

    private static String requireName(String name) {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid file name");
        return name;
    }

    /** 单次上传的状态与暂存档。字段都在 {@link #sessions} 的锁内改动。 */
    private static final class Session {

        final String id;
        final File directory;
        final String name;
        final long size;
        final String sha256;
        final long revision = System.nanoTime();
        private final File part;
        private long touched = System.currentTimeMillis();

        Session(String id, File directory, String name, long size, String sha256) {
            this.id = id;
            this.directory = directory;
            this.name = name;
            this.size = size;
            this.sha256 = sha256;
            this.part = new File(Path.cache("upload"), id + ".part");
        }

        File part() throws IOException {
            File parent = part.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())
                throw new IOException("Unable to create upload staging directory");
            return part;
        }

        boolean matches(File directory, String name, long size, String sha256) {
            return this.directory.equals(directory) && this.name.equals(name) && this.size == size && this.sha256.equalsIgnoreCase(sha256);
        }

        boolean expired() {
            return System.currentTimeMillis() - touched > EXPIRE_MILLIS;
        }

        void touch() {
            touched = System.currentTimeMillis();
        }

        /** 记录已收到的块，便于调用方核对并行传输进度。 */
        void markChunk(long offset) {
            int index = (int) (offset / CHUNK_SIZE);
            while (received.size() <= index) received.add(-1L);
            received.set(index, offset);
        }

        /** 只在持有外层锁时调用。 */
        long offset() {
            return part.length();
        }

        JsonObject describe(boolean parallel) {
            JsonObject result = new JsonObject();
            result.addProperty("id", id);
            result.addProperty("name", name);
            result.addProperty("path", directory.getAbsolutePath());
            result.addProperty("size", size);
            result.addProperty("sha256", sha256);
            long offset = part.isFile() ? part.length() : 0;
            result.addProperty("offset", offset);
            result.addProperty("chunkSize", CHUNK_SIZE);
            result.addProperty("complete", offset == size);
            if (parallel) {
                result.addProperty("revision", revision);
                result.addProperty("threads", THREADS);
                JsonArray chunks = new JsonArray();
                for (int i = 0; i < received.size(); i++) if (received.get(i) >= 0) chunks.add(i);
                result.add("chunks", chunks);
            }
            return result;
        }

        void discard() {
            File parent = part.getParentFile();
            if (part.isFile()) part.delete();
            if (parent != null && parent.isDirectory()) {
                String[] left = parent.list();
                if (left != null && left.length == 0) parent.delete();
            }
        }

        private final java.util.List<Long> received = new java.util.ArrayList<>();
    }

    /** 过期或未知的传输，对应 404。 */
    private static final class NotFound extends Exception {
        NotFound(String message) {
            super(message);
        }
    }
}