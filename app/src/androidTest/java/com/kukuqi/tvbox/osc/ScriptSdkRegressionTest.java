package com.kukuqi.tvbox.osc;

import android.test.InstrumentationTestCase;
import com.github.catvod.Local;
import com.github.catvod.net.Net;
import com.google.gson.*;
import com.kukuqi.tvbox.engine.api.SiteApi;
import com.kukuqi.tvbox.engine.api.config.VodConfig;
import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real HTTP and script engines exercise the public source SDK, including failure paths. */
public class ScriptSdkRegressionTest extends InstrumentationTestCase {
    private NanoHTTPD server;
    private String base;
    private final AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger(), hits = new AtomicInteger();
    private final byte[] payload = new byte[5 * 1024 * 1024];
    private final AtomicInteger ranges = new AtomicInteger();
    private final AtomicInteger rangeRequests = new AtomicInteger();
    private volatile boolean interruptRange;
    private volatile int lastRangeStart;
    @Override protected void setUp() throws Exception {
        super.setUp();
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 251);
        server = new NanoHTTPD("127.0.0.1", 0) {
            @Override public Response serve(IHTTPSession request) {
                String path = request.getUri();
                if (request.getMethod() == Method.HEAD) {
                    byte[] bytes = path.equals("/range") ? payload : "{\"title\":\"sdk\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    // NanoHTTPD 2.3.1 writes a fixed-length HEAD stream unless it is empty.
                    Response response = newFixedLengthResponse(path.equals("/denied") ? Response.Status.FORBIDDEN : Response.Status.OK, "application/octet-stream", new ByteArrayInputStream(new byte[0]), bytes.length);
                    response.setRequestMethod(Method.HEAD);
                    if (path.equals("/range")) { response.addHeader("Accept-Ranges", "bytes"); response.addHeader("ETag", "\"sdk-v1\""); }
                    return response;
                }
                if (path.equals("/range")) {
                    int start = 0, end = payload.length - 1; String range = request.getHeaders().get("range");
                    if (range != null) { String[] limits = range.substring(6).split("-"); start = Integer.parseInt(limits[0]); end = limits.length > 1 ? Integer.parseInt(limits[1]) : payload.length - 1; ranges.incrementAndGet(); }
                    lastRangeStart = start;
                    int count = end - start + 1;
                    if (interruptRange && rangeRequests.getAndIncrement() == 0) count = Math.min(count, 12345);
                    Response response = newFixedLengthResponse(range == null ? Response.Status.OK : Response.Status.PARTIAL_CONTENT, "application/octet-stream", new ByteArrayInputStream(payload, start, count), end - start + 1);
                    response.addHeader("Accept-Ranges", "bytes"); response.addHeader("ETag", "\"sdk-v1\"");
                    if (count < end - start + 1) response.addHeader("Connection", "close");
                    if (range != null) response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + payload.length);
                    return response;
                }
                if (path.equals("/login")) return newFixedLengthResponse("<script>document.cookie='sdk_sid=new; path=/';document.cookie='sdk_token=ok; path=/';</script>login");
                if (path.equals("/denied")) return newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", "{\"error\":\"denied\"}");
                if (path.equals("/html")) return newFixedLengthResponse("<html>not json</html>");
                if (path.equals("/plain")) return newFixedLengthResponse("unquoted");
                if (path.equals("/cookie")) { Response response = newFixedLengthResponse("{}"); response.addHeader("Set-Cookie", "sid=isolated; Path=/"); return response; }
                if (path.equals("/slow")) {
                    peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                    try { Thread.sleep(100); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                    finally { active.decrementAndGet(); }
                    return newFixedLengthResponse(request.getQueryParameterString());
                }
                if (path.equals("/source.js")) return newFixedLengthResponse(js());
                if (path.equals("/source.py")) return newFixedLengthResponse(py());
                hits.incrementAndGet(); return newFixedLengthResponse("{\"title\":\"sdk\"}");
            }
        };
        server.start(); base = "http://127.0.0.1:" + server.getListeningPort();
    }
    @Override protected void tearDown() throws Exception {
        com.kukuqi.tvbox.engine.api.loader.BaseLoader.get().clear();
        server.stop(); super.tearDown();
    }
    public void testJavaHttpErrorsJsonAndDownload() throws Exception {
        File target = new File(getInstrumentation().getTargetContext().getCacheDir(), "sdk-download.json");
        try (Net net = new Net()) {
            assertEquals(403, JsonParser.parseString(net.req(base + "/denied", "{}")).getAsJsonObject().get("code").getAsInt());
            assertTrue(net.get(base + "/denied").contains("denied"));
            for (String path : List.of("/denied", "/html", "/plain")) try { net.json(base + path, "{}"); fail("Invalid JSON request accepted"); } catch (RuntimeException expected) {}
            net.download(base + "/data", "{\"threads\":1}", target);
            assertTrue(new String(Files.readAllBytes(target.toPath()), java.nio.charset.StandardCharsets.UTF_8).contains("sdk"));
            byte[] before = Files.readAllBytes(target.toPath());
            try { net.download(base + "/denied", "{}", target); fail("HTTP 失败必须报告错误"); } catch (IOException expected) {}
            assertTrue(Arrays.equals(before, Files.readAllBytes(target.toPath())));
        } finally { target.delete(); new File(target + ".part").delete(); new File(target + ".part.json").delete(); }
    }
    public void testBatchIsBoundedAndKeepsInputOrder() throws Exception {
        try (Net net = new Net()) {
            JsonArray requests = new JsonArray();
            for (int i = 0; i < 10; i++) {
                JsonObject request = new JsonObject();
                request.addProperty("url", base + "/slow?item=" + i);
                requests.add(request);
            }
            Net.Result[] result = net.batch(requests.toString());
            for (int i = 0; i < 10; i++) assertEquals("item=" + i, result[i].text());
            assertTrue(peak.get() > 1); assertTrue(peak.get() <= 4);
        }
    }
    public void testDownloadParallelRangesAndValidatedResume() throws Exception {
        File target = new File(getInstrumentation().getTargetContext().getCacheDir(), "sdk-range.bin");
        try (Net net = new Net()) {
            net.download(base + "/range", "{\"threads\":4}", target);
            assertTrue("多线程应发起多个分片请求", ranges.get() > 1);
            assertTrue(Arrays.equals(payload, Files.readAllBytes(target.toPath())));
            assertTrue(target.delete());
            interruptRange = true;
            try {
                net.download(base + "/range", "{\"threads\":1}", target);
                fail("截断响应应失败");
            } catch (IOException expected) {
                assertFalse(target.exists());
            }
            net.download(base + "/range", "{\"threads\":1}", target);
            assertEquals("续传必须从已保存的偏移开始", 12345, lastRangeStart);
            assertTrue(Arrays.equals(payload, Files.readAllBytes(target.toPath())));
        } finally { target.delete(); }
    }
    public void testWebLoginClosesAfterCookieChange() throws Exception {
        CountDownLatch cleared = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> {
            android.webkit.CookieManager cookies = android.webkit.CookieManager.getInstance();
            cookies.setCookie(base, "sdk_sid=; Max-Age=0; Path=/");
            cookies.setCookie(base, "sdk_token=; Max-Age=0; Path=/", value -> cleared.countDown());
        });
        assertTrue(cleared.await(3, TimeUnit.SECONDS));
        JsonObject web = JsonParser.parseString("{\"url\":\"" + base + "/login\",\"mode\":\"view\",\"cookie\":[{\"keys\":[\"sdk_sid\",\"sdk_token\"]}]}").getAsJsonObject();
        android.app.Activity activity = getInstrumentation().startActivitySync(new android.content.Intent(getInstrumentation().getTargetContext(), com.kukuqi.tvbox.osc.ui.activity.SourceWebActivity.class)
                .putExtra("web", web.toString()).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
            while (!activity.isFinishing() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
            assertTrue("Login window did not close after both cookies changed", activity.isFinishing());
        } finally { getInstrumentation().runOnMainSync(activity::finish); }
    }
    public void testSessionAndLocalIsolation() throws Exception {
        Local first = new Local("sdk-first"), second = new Local("sdk-second");
        first.set("shared", "{\"n\":1}"); second.delete("shared");
        assertEquals("null", second.get("shared")); assertTrue(first.get("shared").contains("1")); first.delete("shared");
        Net parent = new Net(); Net firstSession = parent.session(), secondSession = parent.session();
        firstSession.req(base + "/cookie", "{}");
        assertTrue(firstSession.getCookie(base).contains("sid=isolated")); assertEquals("", secondSession.getCookie(base));
        parent.close(); try { secondSession.session(); fail("Parent close must close children"); } catch (IllegalStateException expected) {}
    }
    public void testCacheClearCancelsLoadAndRejectsLateWrite() throws Exception {
        try (Net net = new Net()) {
            net.clearCache(); CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1), exited = new CountDownLatch(1);
            net.refresh("k", "{\"ttl\":10000}", () -> { entered.countDown(); try { finish.await(); } catch (InterruptedException ignored) { finish.await(); } finally { exited.countDown(); } return "\"old\""; });
            assertTrue(entered.await(3, TimeUnit.SECONDS)); net.clearCache(); finish.countDown(); assertTrue(exited.await(3, TimeUnit.SECONDS));
            assertEquals("\"new\"", net.cached("k", "{\"ttl\":10000}", () -> "\"new\""));
            assertEquals("\"new\"", net.cached("k", "{\"ttl\":10000}", () -> { throw new AssertionError("Cache loader ran twice"); }));
        }
    }
    public void testQuickJsSdkNetworkCacheAndCookieSession() throws Exception { script("js"); }
    public void testPythonSdkNetworkCacheAndCookieSession() throws Exception { script("py"); }
    private void script(String language) throws Exception {
        JsonObject config = new JsonObject(), site = new JsonObject(); site.addProperty("key", "sdk-" + language); site.addProperty("type", 3);
        site.addProperty("api", base + "/source." + language); site.addProperty("ext", base); JsonArray sites = new JsonArray(); sites.add(site); config.add("sites", sites);
        VodConfig.get().accept(base + "/config.json", "sdk-" + language, config);
        assertEquals("sdk", SiteApi.homeContent(VodConfig.get().getSite("sdk-" + language)).getTypes().get(0).getTypeName());
        assertEquals(2, SiteApi.action("sdk-" + language, "login").getWeb().getAsJsonArray("cookie").get(0).getAsJsonObject().getAsJsonArray("keys").size());
    }
    public void testQuickJsDestroyPromiseIsBounded() throws Exception {
        script("js"); long start = android.os.SystemClock.elapsedRealtime();
        com.kukuqi.tvbox.engine.api.loader.BaseLoader.get().clear();
        assertTrue("destroy exceeded its deadline", android.os.SystemClock.elapsedRealtime() - start < 6000);
    }
    private String js() {
        return "import {Spider,net,local} from 'catvod';export default class extends Spider {"
                + "init(ext){this.base=ext;local.set('init',{ok:true});} async homeContent(){"
                + "if(!local.get('init').ok)throw Error('local');const url=this.base+'/data';"
                + "if(!net.getUserAgent())throw Error('host-ua');"
                + "if(!net.setCookie(this.base,'sdk_host=1; Path=/')||!net.getCookie(this.base).includes('sdk_host=1'))throw Error('host-cookie');"
                + "const data=await net.json(url);const list=await net.batch([{url},{url}]);if(list.length!==2)throw Error('batch');"
                + "net.clearCache();let n=0;await net.cached('k',{ttl:10000,persist:true},()=>++n);await net.cached('k',{ttl:10000},()=>++n);if(n!==1)throw Error('cache');"
                + "const a=net.session(),b=net.session();await a.req(this.base+'/cookie');if(!a.getCookie(this.base).includes('isolated')||b.getCookie(this.base)!=='')throw Error('session');a.close();b.close();"
                + "let failed=false;try{await net.json(this.base+'/denied');}catch(e){failed=true;}if(!failed)throw Error('http');"
                + "return {class:[{type_id:'1',type_name:data.title}]};} homeVideoContent(){return {list:[]};}"
                + "action(){return {web:{url:this.base+'/login',cookie:[{keys:['sid','token']}]}};} destroy(){return new Promise(()=>{});}}";
    }
    private String py() {
        return "from base.spider import Spider as Base\nclass Spider(Base):\n def init(self,extend=''):\n  self.base=extend\n  self.local.set('init',{'ok':True})\n"
                + " def homeContent(self,filter):\n  assert self.local.get('init')['ok']\n  url=self.base+'/data'\n  data=self.net.json(url)\n  assert len(self.net.batch([{url},{url}]))==2\n"
                + "  self.net.clearCache()\n  assert self.net.cached('k',{'ttl':10000},lambda: {'n':1})=={'n':1}\n  assert self.net.cachedText('k',{},lambda: 'bad')=='{\"n\": 1}'\n"
                + "  a=self.net.session()\n  b=self.net.session()\n  a.http(self.base+'/cookie')\n  assert 'isolated' in a.getCookie(self.base) and b.getCookie(self.base)==''\n  a.close()\n  b.close()\n"
                + "  return {'class':[{'type_id':'1','type_name':data['title']}]}\n def homeVideoContent(self): return {'list':[]}\n"
                + " def action(self,action): return {'web':{'url':self.base+'/login','cookie':[{'keys':['sid','token']}]}}\n def destroy(self): pass\n";
    }
}
