package com.kukuqi.tvbox.osc;

import android.test.InstrumentationTestCase;
import com.github.catvod.net.Net;
import com.github.catvod.net.NetOptions;
import com.google.gson.JsonParser;
import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证 source SDK 补齐的 Net 公开方法在真实 Android 运行时下的行为。
 *
 * <p>这些方法此前只在编译期确认存在，这里补上运行期证据：结构化请求、拆分返回、
 * 异步下载、缓存读取与 Cookie 布尔语义。</p>
 */
public class NetApiRegressionTest extends InstrumentationTestCase {

    private NanoHTTPD server;
    private String base;
    private final AtomicInteger hits = new AtomicInteger();

    @Override protected void setUp() throws Exception {
        super.setUp();
        server = new NanoHTTPD("127.0.0.1", 0) {
            @Override public Response serve(IHTTPSession request) {
                String path = request.getUri();
                hits.incrementAndGet();
                // NanoHTTPD 2.3.1 对 HEAD 也写定长流，除非正文为空；Download 会先发 HEAD 探测长度
                if (request.getMethod() == Method.HEAD) {
                    long length = path.equals("/file") ? 64 * 1024 : 0;
                    Response head = newFixedLengthResponse(Response.Status.OK, "application/octet-stream", new ByteArrayInputStream(new byte[0]), length);
                    head.setRequestMethod(Method.HEAD);
                    return head;
                }
                if (path.equals("/json")) {
                    byte[] body = "{\"title\":\"sdk\",\"page\":2}".getBytes(StandardCharsets.UTF_8);
                    Response response = newFixedLengthResponse(Response.Status.OK, "application/json", new ByteArrayInputStream(body), body.length);
                    response.addHeader("X-Probe", "yes");
                    return response;
                }
                if (path.equals("/denied")) {
                    byte[] body = "no".getBytes(StandardCharsets.UTF_8);
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", new ByteArrayInputStream(body), body.length);
                }
                if (path.equals("/file")) {
                    byte[] body = new byte[64 * 1024];
                    for (int i = 0; i < body.length; i++) body[i] = (byte) (i % 251);
                    return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", new ByteArrayInputStream(body), body.length);
                }
                byte[] empty = new byte[0];
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", new ByteArrayInputStream(empty), 0);
            }
        };
        server.start();
        base = "http://127.0.0.1:" + server.getListeningPort();
    }

    @Override protected void tearDown() throws Exception {
        if (server != null) server.stop();
        super.tearDown();
    }

    /** request 返回结构化结果：状态码、响应头与正文都要对得上。 */
    public void testRequestReturnsStructuredResult() {
        Net net = new Net();
        try {
            Net.Result result = net.request(base + "/json", new NetOptions());
            assertEquals(200, result.code);
            assertTrue(result.error == null);
            assertTrue(result.text().contains("sdk"));
            // 响应头查找不区分大小写，脚本按任意大小写都能取到
            assertEquals("yes", result.headers.get("x-probe"));
            assertEquals("yes", result.headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase("X-Probe")).findFirst().orElseThrow().getValue());
            assertNull(result.headers.get("absent-header"));
            // 非 2xx 不抛异常，交给调用方判断
            Net.Result denied = net.request(base + "/denied", new NetOptions());
            assertEquals(403, denied.code);
            assertNull(denied.error);
        } finally {
            net.close();
        }
    }

    /** jsonText 对非 2xx 报错，对 2xx 返回原正文。 */
    public void testJsonTextThrowsOnNonSuccess() {
        Net net = new Net();
        try {
            assertTrue(net.jsonText(base + "/json", "{}").contains("page"));
            try {
                net.jsonText(base + "/denied", "{}");
                fail("非 2xx 应报告错误");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("403"));
            }
        } finally {
            net.close();
        }
    }

    /** reqParts 以 [元数据 JSON, 内容] 返回。 */
    public void testReqPartsShape() {
        Net net = new Net();
        try {
            Object[] parts = net.reqParts(base + "/json", "{}");
            assertEquals(2, parts.length);
            assertEquals(200, JsonParser.parseString((String) parts[0]).getAsJsonObject().get("code").getAsInt());
            assertTrue(String.valueOf(parts[1]).contains("sdk"));
        } finally {
            net.close();
        }
    }

    /** requestAsync 与 enqueue 都要把结果送达。 */
    public void testAsyncRequestDelivers() throws Exception {
        Net net = new Net();
        try {
            Net.Result result = net.requestAsync(base + "/json", new NetOptions()).get(15, TimeUnit.SECONDS);
            assertEquals(200, result.code);
            CompletableFuture<Net.Result> callback = new CompletableFuture<>();
            net.enqueue(base + "/denied", new NetOptions(), callback::complete);
            assertEquals(403, callback.get(15, TimeUnit.SECONDS).code);
        } finally {
            net.close();
        }
    }

    /** 异步缓存命中后不再装载，peek 可以读取已保存的 JSON。 */
    public void testCachedValueAndAsync() throws Exception {
        try (Net net = new Net()) {
            assertNull(net.peek("absent"));
            AtomicInteger loaded = new AtomicInteger();
            String options = "{\"ttl\":60000}";
            assertEquals("\"payload\"", net.cachedAsync("k1", options, () -> {
                loaded.incrementAndGet();
                return CompletableFuture.completedFuture("\"payload\"");
            }).get(15, TimeUnit.SECONDS));
            assertEquals("\"payload\"", net.cachedAsync("k1", options, () -> {
                throw new AssertionError("命中缓存后不应再次装载");
            }).get(15, TimeUnit.SECONDS));
            assertEquals("\"payload\"", net.peek("k1"));
            assertEquals(1, loaded.get());
        }
    }

    /** setCookie 用布尔值报告是否真的写入。 */
    public void testSetCookieBooleanSemantics() {
        Net net = new Net().session();
        try {
            assertFalse(net.setCookie(null, "a=b"));
            assertFalse(net.setCookie(base, null));
            assertTrue(net.setCookie(base + "/", "probe=1; Path=/"));
            assertTrue(net.getCookie(base + "/").contains("probe=1"));
        } finally {
            net.close();
        }
    }

    /** downloadAsync 落到真实文件，字节数一致。 */
    public void testDownloadAsyncWritesFile() throws Exception {
        Net net = new Net();
        File target = new File(getInstrumentation().getTargetContext().getCacheDir(), "net-api-" + System.nanoTime() + ".bin");
        try {
            String written = net.downloadAsync(base + "/file", "{}", target).get(30, TimeUnit.SECONDS);
            assertNotNull(written);
            assertEquals(target.getAbsolutePath(), written);
            assertEquals(64 * 1024L, target.length());
        } finally {
            if (target.exists() && !target.delete()) target.deleteOnExit();
            net.close();
        }
    }

    /** 关闭后的实例必须返回失败结果，不能再发送网络请求。 */
    public void testClosedNetRejectsFurtherUse() {
        Net net = new Net();
        net.close();
        Net.Result result = net.request(base + "/json", new NetOptions());
        assertNotNull("关闭后的请求应明确返回失败", result.error);
        assertEquals("", result.code);
        assertEquals(0, hits.get());
    }
}
