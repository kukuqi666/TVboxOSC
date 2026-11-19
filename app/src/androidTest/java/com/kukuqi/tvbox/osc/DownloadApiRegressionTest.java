package com.kukuqi.tvbox.osc;

import android.test.InstrumentationTestCase;
import com.github.catvod.net.Download;
import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 验证补齐的 Download 能力在真机上的行为：标签、回调式启动、缓存保护与过期清理、
 * 分片目录与跨分区落盘。
 */
public class DownloadApiRegressionTest extends InstrumentationTestCase {

    private NanoHTTPD server;
    private String base;
    private File root;
    private final AtomicInteger resumedAt = new AtomicInteger(-1);
    private final AtomicInteger resumeRequests = new AtomicInteger();
    private final AtomicInteger hits = new AtomicInteger();
    private static final int SIZE = 96 * 1024;

    @Override protected void setUp() throws Exception {
        super.setUp();
        root = new File(getInstrumentation().getTargetContext().getCacheDir(), "dl-" + System.nanoTime());
        assertTrue(root.isDirectory() || root.mkdirs());
        server = new NanoHTTPD("127.0.0.1", 0) {
            @Override public Response serve(IHTTPSession request) {
                hits.incrementAndGet();
                String path = request.getUri();
                byte[] body = new byte[SIZE];
                for (int i = 0; i < body.length; i++) body[i] = (byte) (i % 251);
                if (request.getMethod() == Method.HEAD) {
                    if (path.equals("/missing")) {
                        Response gone = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", new ByteArrayInputStream(new byte[0]), 0);
                        gone.setRequestMethod(Method.HEAD);
                        return gone;
                    }
                    // NanoHTTPD 2.3.1 对 HEAD 也写定长流，除非正文为空
                    Response head = newFixedLengthResponse(Response.Status.OK, "application/octet-stream", new ByteArrayInputStream(new byte[0]), body.length);
                    head.setRequestMethod(Method.HEAD);
                    head.addHeader("Accept-Ranges", "bytes");
                    head.addHeader("ETag", "\"dl-v1\"");
                    return head;
                }
                if (path.equals("/missing")) {
                    byte[] none = "gone".getBytes(StandardCharsets.UTF_8);
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", new ByteArrayInputStream(none), none.length);
                }
                if (path.equals("/resume")) {
                    String range = request.getHeaders().get("range");
                    int start = range == null ? 0 : Integer.parseInt(range.substring(6).split("-")[0]);
                    resumedAt.set(start);
                    int bytes = resumeRequests.getAndIncrement() == 0 ? SIZE / 2 : body.length - start;
                    Response response = newFixedLengthResponse(range == null ? Response.Status.OK : Response.Status.PARTIAL_CONTENT,
                            "application/octet-stream", new ByteArrayInputStream(body, start, bytes), body.length - start);
                    response.addHeader("ETag", "\"dl-v1\"");
                    response.addHeader("Accept-Ranges", "bytes");
                    if (bytes < body.length - start) response.addHeader("Connection", "close");
                    if (range != null) response.addHeader("Content-Range", "bytes " + start + "-" + (SIZE - 1) + "/" + SIZE);
                    return response;
                }
                if (path.equals("/slow")) {
                    Response slow = newFixedLengthResponse(Response.Status.OK, "application/octet-stream", new ByteArrayInputStream(body), body.length);
                    slow.addHeader("Accept-Ranges", "bytes");
                    slow.addHeader("ETag", "\"dl-v1\"");
                    return slow;
                }
                Response response = newFixedLengthResponse(Response.Status.OK, "application/octet-stream", new ByteArrayInputStream(body), body.length);
                response.addHeader("Accept-Ranges", "bytes");
                response.addHeader("ETag", "\"dl-v1\"");
                return response;
            }
        };
        server.start();
        base = "http://127.0.0.1:" + server.getListeningPort();
    }

    @Override protected void tearDown() throws Exception {
        if (server != null) server.stop();
        Download.clearCache(root);
        super.tearDown();
    }

    /** tag 只是链式配置，不能影响下载结果。 */
    public void testTagDoesNotBreakDownload() throws Exception {
        File target = new File(root, "tag.bin");
        File file = Download.create(base + "/file", target).tag("probe-tag").get();
        assertEquals(SIZE, file.length());
    }

    /** start 通过回调交付结果，成功与失败都要回调且只回调一次。 */
    public void testStartDeliversCallbacksOnce() throws Exception {
        File target = new File(root, "start.bin");
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<File> completed = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger progress = new AtomicInteger();
        AtomicInteger terminals = new AtomicInteger();
        Download.create(base + "/file", target).start(new Download.Callback() {
            @Override public void progress(long total, long downloaded, long speed) { progress.incrementAndGet(); }
            @Override public void success(File file) { terminals.incrementAndGet(); completed.set(file); done.countDown(); }
            @Override public void error(String error) { terminals.incrementAndGet(); failure.set(new java.io.IOException(error)); done.countDown(); }
        });
        assertTrue("等待回调超时", done.await(30, TimeUnit.SECONDS));
        // 给潜在的第二回调留出窗口，再断言只到一个
        Thread.sleep(300);
        assertNull(failure.get());
        assertNotNull(completed.get());
        assertEquals(SIZE, completed.get().length());
        assertEquals("终态回调只能有一次", 1, terminals.get());
        assertTrue("必须收到进度", progress.get() > 0);
    }

    /** 失败路径必须走 error，不能静默。 */
    public void testStartReportsError() throws Exception {
        File target = new File(root, "fail.bin");
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Download.create(base + "/missing", target).start(new Download.Callback() {
            @Override public void success(File file) { done.countDown(); }
            @Override public void error(String error) { failure.set(new java.io.IOException(error)); done.countDown(); }
        });
        assertTrue("等待失败回调超时", done.await(30, TimeUnit.SECONDS));
        assertNotNull(failure.get());
    }

    /** retain 保护的路径不能被 clearCache 删除，逐个释放后才可清理。 */
    public void testRetainProtectsFromClearCache() throws Exception {
        File keep = new File(root, "keep.bin");
        File drop = new File(root, "drop.bin");
        write(keep, 16);
        write(drop, 16);
        Closeable first = Download.retain(keep);
        Closeable second = Download.retain(keep);
        Download.clearCache(keep);
        assertTrue("被 retain 的文件不应被删除", keep.exists());
        first.close();
        Download.clearCache(keep);
        assertTrue("仍有句柄时不应删除", keep.exists());
        second.close();
        Download.clearCache(keep);
        assertFalse("全部释放后应可删除", keep.exists());
        // 未保护的文件正常删除
        Download.clearCache(drop);
        assertFalse(drop.exists());
    }

    /** 受保护文件所在目录不会被整体删除。 */
    public void testClearCacheKeepsParentOfRetained() throws Exception {
        File keep = new File(root, "nested/keep.bin");
        assertTrue(keep.getParentFile().isDirectory() || keep.getParentFile().mkdirs());
        write(keep, 8);
        try (Closeable handle = Download.retain(keep)) {
            Download.clearCache(root);
            assertTrue("受保护文件应留存", keep.exists());
        }
        Download.clearCache(root);
        assertFalse(keep.exists());
    }

    /** clearExpired 只清理过期残留，未过期与目标文件都不动。 */
    public void testClearExpiredRespectsAge() throws Exception {
        File stale = new File(root, "a".repeat(64) + ".part");
        File fresh = new File(root, "b".repeat(64) + ".part");
        File state = new File(stale.getPath() + ".properties");
        File done = new File(root, "done.bin");
        write(stale, 8);
        write(fresh, 8);
        write(state, 8);
        write(done, 8);
        long old = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
        assertTrue(stale.setLastModified(old));
        assertTrue(state.setLastModified(old));
        Download.clearExpired(root);
        assertFalse("过期分片应被清理", stale.exists());
        assertFalse("过期状态应被清理", state.exists());
        assertTrue("未过期分片应留存", fresh.exists());
        assertTrue("目标文件不是残留，不应被清理", done.exists());
    }

    /** directory 让分片与状态落在指定目录，目标仍在原位置。 */
    public void testDirectorySeparatesPartFiles() throws Exception {
        File work = new File(root, "work");
        assertTrue(work.isDirectory() || work.mkdirs());
        File target = new File(root, "sep.bin");
        File file = Download.create(base + "/file", target).directory(work).get();
        assertEquals(SIZE, file.length());
        assertEquals(target.getAbsolutePath(), file.getAbsolutePath());
        File[] leftovers = work.listFiles((dir, name) -> name.endsWith(".part") || name.endsWith(".part.properties"));
        assertEquals("完成后中间文件应清理", 0, leftovers == null ? 0 : leftovers.length);
    }

    /** previousDirectory 能把老位置的分片接续过来，而不是重新下载。 */
    public void testPreviousDirectoryResumesLegacyPart() throws Exception {
        File legacy = new File(root, "legacy");
        assertTrue(legacy.isDirectory() || legacy.mkdirs());
        File target = new File(root, "resumed.bin");
        try {
            Download.create(base + "/resume", target).directory(legacy).threads(1).get(new android.os.CancellationSignal(), null);
            fail("截断响应应失败");
        } catch (java.io.IOException expected) {
            assertFalse("失败不能留下半截目标", target.exists());
        }
        File[] parts = legacy.listFiles((dir, name) -> name.endsWith(".part"));
        assertNotNull(parts);
        assertEquals(1, parts.length);
        assertEquals(SIZE / 2, parts[0].length());
        File file = Download.create(base + "/resume", target).threads(1).previousDirectory(legacy).get();
        assertEquals("应只请求后半段", SIZE / 2, resumedAt.get());
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        assertEquals(SIZE, bytes.length);
        for (int i = 0; i < bytes.length; i++) assertEquals((byte) (i % 251), bytes[i]);
        assertFalse("旧分片应被搬走", parts[0].exists());
    }

    /** 长度未知时也先写分片，避免失败的请求把目标写成半截。 */
    public void testUnknownLengthDoesNotLeavePartialTarget() throws Exception {
        File target = new File(root, "unknown.bin");
        try {
            Download.create(base + "/missing", target).get(new android.os.CancellationSignal(), null);
            fail("404 应失败");
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
        assertFalse("失败后不应留下目标文件", target.exists());
    }

    private void write(File file, int bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) assertTrue(parent.mkdirs());
        try (FileOutputStream output = new FileOutputStream(file)) {
            for (int i = 0; i < bytes; i++) output.write(i);
        }
    }
}