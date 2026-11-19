package com.kukuqi.tvbox.osc;

import android.test.InstrumentationTestCase;

import com.github.catvod.utils.Auth;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Task;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.UriUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 本轮补齐的 utils 方法必须真能工作，不能只是编译通过。 */
public class UtilsGapTest extends InstrumentationTestCase {

    // ---------- UriUtil ----------
    public void testUriUtilAccessors() {
        String url = "https://Example.COM:8443/a/b/File.txt?q=1#f";
        assertEquals("https", UriUtil.scheme(url));
        assertEquals("example.com", UriUtil.host(url));
        assertEquals("File.txt", UriUtil.path(url));
        assertEquals("https://Example.COM:8443/a/b/File.txt", UriUtil.stripQueryAndFragment(url));
        assertEquals("File.txt", UriUtil.getName(url));
    }

    public void testUriUtilHandlesNull() {
        assertEquals("", UriUtil.scheme((String) null));
        assertEquals("", UriUtil.host((String) null));
        assertEquals("", UriUtil.path((String) null));
    }

    public void testUriUtilLocalPath() {
        assertEquals("file", UriUtil.scheme("/sdcard/movie.mp4"));
        assertEquals("movie.mp4", UriUtil.path("/sdcard/movie.mp4"));
    }

    // ---------- Crypto ----------
    public void testRandomUrlSafeLengthAndCharset() {
        // 参数是字节数：32 字节 base64url 无填充 = 43 字符
        String value = Crypto.randomUrlSafe(32);
        assertEquals("32 字节应为 43 字符", 43, value.length());
        assertEquals("30 字节应为 40 字符", 40, Crypto.randomUrlSafe(30).length());
        assertTrue("不应含 +/=", value.indexOf('+') < 0 && value.indexOf('/') < 0 && value.indexOf('=') < 0);
        assertFalse("两次应不同", value.equals(Crypto.randomUrlSafe(32)));
    }

    public void testRandomUrlSafeRejectsBadSize() {
        assertEquals("", Crypto.randomUrlSafe(0));
        assertEquals("", Crypto.randomUrlSafe(-1));
        assertEquals("", Crypto.randomUrlSafe(9999));
    }

    // ---------- Json ----------
    public void testJsonStrictParses() {
        assertEquals("1", Json.strict("[1,2,3]").getAsJsonArray().get(0).getAsString());
    }

    public void testJsonStrictRejectsBlank() {
        try { Json.strict("   "); fail("空白应被拒绝"); }
        catch (IllegalArgumentException expected) { }
        try { Json.strict(null); fail("null 应被拒绝"); }
        catch (IllegalArgumentException expected) { }
    }

    // ---------- Trans ----------
    public void testConvertMapsLanguages() {
        assertEquals("转换", Trans.convert("轉換", "zh-cn"));
        assertEquals("轉換", Trans.convert("转换", "zh-tw"));
        assertEquals("abc", Trans.convert("abc", "en"));
        assertNull(Trans.convert(null, "zh-cn"));
    }

    // ---------- Path ----------
    public void testPathReadOrThrowAndAtomicallyWrite() throws Exception {
        File target = new File(getInstrumentation().getTargetContext().getCacheDir(), "gap-atomic.txt");
        if (target.exists()) target.delete();
        byte[] data = "原子写入测试".getBytes("UTF-8");
        Path.writeAtomically(target, data);
        assertTrue("文件应存在", target.exists());
        assertEquals("内容应一致", "原子写入测试", Path.readOrThrow(new FileInputStream(target)));
        assertFalse("不应残留临时文件",
                new File(target.getParentFile(), "." + target.getName()).exists());
    }

    public void testPathRequireSpace() throws Exception {
        File dir = getInstrumentation().getTargetContext().getCacheDir();
        Path.requireSpace(dir, 1024); // 小需求应通过
        try { Path.requireSpace(dir, Long.MAX_VALUE / 4); fail("超额应抛异常"); }
        catch (java.io.IOException expected) { }
    }

    public void testPathChmodReturnsFile() {
        File f = new File(getInstrumentation().getTargetContext().getCacheDir(), "gap-chmod.txt");
        assertSame(f, Path.chmod(f));
    }

    // ---------- Task ----------
    public void testTaskCallbackInvokesSuccess() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> got = new AtomicReference<>();
        com.google.common.util.concurrent.FutureCallback<String> cb =
                Task.callback(v -> { got.set(v); latch.countDown(); });
        cb.onSuccess("ok");
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals("ok", got.get());
    }

    public void testTaskCallbackRoutesFailure() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> err = new AtomicReference<>();
        com.google.common.util.concurrent.FutureCallback<String> cb =
                Task.callback(v -> { }, e -> { err.set(e.getMessage()); latch.countDown(); });
        cb.onFailure(new RuntimeException("boom"));
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals("boom", err.get());
    }

    public void testTaskCallbackWithoutFailureHandlerSwallows() {
        Task.callback(v -> { }).onFailure(new RuntimeException("ignored"));
    }

    // ---------- Auth ----------
    public void testAuthCaptureBasicSameOrigin() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", Auth.basic("user:pass"));
        Auth.Context ctx = Auth.capture("https://host/dav/a.txt", headers,
                "https://host/dav/b.txt", Auth.basic("user:pass"));
        assertNotNull("同源 Basic 应捕获", ctx);
        Map<String, String> out = ctx.getHeaders("https://host/dav/c.txt", new HashMap<>());
        assertEquals(Auth.basic("user:pass"), out.get("Authorization"));
    }

    public void testAuthCaptureRejectsCrossOrigin() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", Auth.basic("user:pass"));
        assertNull("跨域必须拒绝", Auth.capture("https://host/a", headers,
                "https://evil/a", Auth.basic("user:pass")));
    }

    public void testAuthCaptureRejectsMismatchedAuthorization() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", Auth.basic("user:pass"));
        assertNull("凭据不符必须拒绝", Auth.capture("https://host/a", headers,
                "https://host/b", Auth.basic("other:x")));
    }

    public void testAuthContextDoesNotLeakToForeignOrigin() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", Auth.basic("user:pass"));
        Auth.Context ctx = Auth.capture("https://host/a", headers, "https://host/b", Auth.basic("user:pass"));
        assertNotNull(ctx);
        assertEquals("外域不得附带凭据", "https://evil.com/x", ctx.getUrl("https://evil.com/x", new HashMap<>()));
        assertTrue(ctx.getHeaders("https://evil.com/x", new HashMap<>()).isEmpty());
    }

    public void testAuthTokenCapture() {
        Auth.Context ctx = Auth.capture("https://host/a?auth=tok123", new HashMap<>(),
                "https://host/b?auth=tok123", null);
        assertNotNull("token 一致应捕获", ctx);
        assertTrue(ctx.getUrl("https://host/c", new HashMap<>()).contains("auth=tok123"));
    }
}