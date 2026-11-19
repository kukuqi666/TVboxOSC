package com.kukuqi.tvbox.engine.server.process;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.kukuqi.tvbox.engine.server.Nano;
import com.kukuqi.tvbox.engine.server.impl.Process;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

/**
 * Device-side download tasks behind {@code /download/*}: start, status, pause, resume and cancel.
 *
 * <p>One task runs at a time, matching the documented contract, so the phone never saturates its
 * network on behalf of the remote page. Pause and cancel set a flag and let the copying loop stop at
 * the next buffer boundary; the transient {@code pausing}/{@code cancelling} states exist because the
 * loop cannot stop instantly. A paused task keeps its staged file, so {@code resume} continues from
 * the byte it reached instead of starting over.</p>
 */
public class DownloadTask implements Process {

    private static final String PREFIX = "/download/";
    private static final String STAGING = ".tv-downloads";
    private static final int BUFFER = 16 * 1024;

    private final Object lock = new Object();
    private Task current;

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith(PREFIX);
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            String action = url.substring(PREFIX.length());
            if (action.equals("start")) return Nano.ok(start(params).toString());
            if (action.equals("status")) return Nano.ok(status(params).toString());
            if (action.equals("pause")) return Nano.ok(control(params, state -> pause(state)).toString());
            if (action.equals("resume")) return Nano.ok(control(params, state -> resume(state)).toString());
            if (action.equals("cancel")) return Nano.ok(control(params, state -> cancel(state)).toString());
            return Nano.error(Response.Status.NOT_FOUND, "unknown action " + action);
        } catch (NotFound e) {
            return Nano.error(Response.Status.NOT_FOUND, e.getMessage());
        } catch (Conflict e) {
            return Nano.error(Response.Status.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            return Nano.error(Response.Status.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private JsonObject start(Map<String, String> params) throws IOException, Conflict {
        String url = require(params, "url").trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw new IllegalArgumentException("url 需为 HTTP(S)");
        File directory = Path.local(require(params, "path")).getCanonicalFile();
        if (!directory.isDirectory()) throw new IllegalArgumentException("Destination directory not found");
        String name = name(params.get("name"), url);
        synchronized (lock) {
            if (current != null && current.running()) throw new Conflict("已有下载任务进行中");
            File staging = new File(Path.cache(STAGING), UUID.randomUUID().toString().replace("-", "") + ".part");
            current = new Task(url, directory, name, staging);
            current.begin();
            return current.json();
        }
    }

    private JsonObject status(Map<String, String> params) throws NotFound, Conflict {
        String id = params.get("id");
        synchronized (lock) {
            Task task = current;
            if (task == null) throw new NotFound("尚无下载任务");
            if (id != null && !id.trim().isEmpty() && !task.id.equals(id.trim())) throw new Conflict("任务 id 不符");
            return task.json();
        }
    }

    private JsonObject control(Map<String, String> params, Action action) throws NotFound, Conflict {
        String id = require(params, "id").trim();
        synchronized (lock) {
            Task task = current;
            if (task == null) throw new NotFound("尚无下载任务");
            if (!task.id.equals(id)) throw new Conflict("任务 id 不符");
            action.apply(task);
            return task.json();
        }
    }

    private void pause(Task task) {
        if (task.is("running")) task.state = "pausing";
    }

    private void resume(Task task) {
        if (task.is("paused") || task.is("failed")) {
            task.error = null;
            task.state = "running";
            task.restart();
        }
    }

    private void cancel(Task task) {
        if (task.is("completed") || task.is("cancelled") || task.is("failed")) return;
        task.state = "cancelling";
        task.stopped = true;
    }

    private static String name(String raw, String url) {
        if (raw != null && !raw.trim().isEmpty()) return requireFileName(raw.trim());
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int slash = path.lastIndexOf('/');
        String tail = slash >= 0 ? path.substring(slash + 1) : path;
        if (tail.isEmpty()) return "download";
        if (!tail.isEmpty() && !tail.contains(".")) return "download";
        try {
            return requireFileName(java.net.URLDecoder.decode(tail, "UTF-8"));
        } catch (Exception e) {
            return "download";
        }
    }

    private static String requireFileName(String name) {
        String clean = name.replace('/', '_').replace('\\', '_').trim();
        if (clean.isEmpty() || clean.equals(".") || clean.equals("..") || clean.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid file name");
        return clean;
    }

    private static String require(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private interface Action {
        void apply(Task task) throws NotFound, Conflict;
    }

    private static final class NotFound extends Exception {
        NotFound(String message) {
            super(message);
        }
    }

    private static final class Conflict extends Exception {
        Conflict(String message) {
            super(message);
        }
    }

    /** 一次下载的完整状态；可变字段只在 {@link #lock} 或工作线程内改动。 */
    private final class Task implements Runnable {

        final String id = UUID.randomUUID().toString().replace("-", "");
        final String url;
        final File directory;
        final String name;
        final File staging;
        final AtomicLong downloaded = new AtomicLong();
        volatile long total = -1;
        volatile long speed;
        volatile String state = "running";
        volatile String error;
        volatile boolean stopped;
        private Thread worker;

        Task(String url, File directory, String name, File staging) {
            this.url = url;
            this.directory = directory;
            this.name = name;
            this.staging = staging;
        }

        boolean is(String value) {
            return value.equals(state);
        }

        boolean running() {
            return is("running") || is("pausing") || is("cancelling");
        }

        void begin() {
            download(this);
        }

        @Override
        public void run() {
            download(this);
        }

        synchronized void restart() {
            download(this);
        }

        JsonObject json() {
            JsonObject result = new JsonObject();
            result.addProperty("id", id);
            result.addProperty("name", name);
            result.addProperty("path", directory.getAbsolutePath());
            result.addProperty("state", state);
            result.addProperty("total", total);
            result.addProperty("downloaded", downloaded.get());
            result.addProperty("speed", speed);
            if (error != null) result.addProperty("error", error);
            return result;
        }

        private void download(Task task) {
            synchronized (lock) {
                if (worker != null && worker.isAlive()) return;
                worker = new Thread(this::transfer, "download-task");
                worker.setDaemon(true);
                worker.start();
            }
        }

        private void transfer() {
            File folder = staging.getParentFile();
            try {
                if (folder != null && !folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) throw new IOException("Unable to create staging directory");
                long from = staging.isFile() ? staging.length() : 0;
                OkHttpClient client = OkHttp.client();
                Request.Builder request = new Request.Builder().url(url);
                if (from > 0) request.header("Range", "bytes=" + from + "-");
                okhttp3.Response response = client.newCall(request.build()).execute();
                try (ResponseBody body = response.body()) {
                    if (body == null) throw new IOException("空回应");
                    int code = response.code();
                    boolean append = from > 0 && code == 206;
                    if (from > 0 && code != 206) from = 0;
                    if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
                    total = append ? from + body.contentLength() : body.contentLength();
                    downloaded.set(from);
                    copy(body.byteStream(), from, append);
                }
            } catch (Throwable e) {
                if (stopped) { settle(); return; }
                error = String.valueOf(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                state = "failed";
            }
        }

        /** 复制循环停下后把 pausing／cancelling 落到终态，避免状态停在过渡值。 */
        private void settle() {
            speed = 0;
            if (is("cancelling")) {
                state = "cancelled";
                if (staging.isFile() && !staging.delete()) staging.deleteOnExit();
                return;
            }
            if (is("pausing")) state = "paused";
        }

        private void copy(InputStream in, long from, boolean append) throws IOException {
            long started = System.currentTimeMillis();
            long lastTick = started;
            long lastBytes = from;
            try (FileOutputStream out = new FileOutputStream(staging, append)) {
                byte[] buffer = new byte[BUFFER];
                for (int read; (read = in.read(buffer)) > 0; ) {
                    if (stopped) { settle(); return; }
                    out.write(buffer, 0, read);
                    long now = downloaded.addAndGet(read);
                    long clock = System.currentTimeMillis();
                    if (clock - lastTick >= 1000) {
                        speed = (now - lastBytes) * 1000 / Math.max(1, clock - lastTick);
                        lastTick = clock;
                        lastBytes = now;
                    }
                    if (total == 0) total = -1;
                }
            }
            if (stopped) { settle(); return; }
            long have = staging.length();
            if (total > 0 && have != total) throw new IOException("下载不完整 " + have + "/" + total);
            File target = new File(directory, name).getCanonicalFile();
            if (!target.getParentFile().equals(directory)) throw new SecurityException("Invalid file name");
            if (!staging.renameTo(target)) {
                java.nio.file.Files.copy(staging.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (!staging.delete()) staging.deleteOnExit();
            }
            downloaded.set(have);
            speed = 0;
            state = "completed";
        }
    }
}