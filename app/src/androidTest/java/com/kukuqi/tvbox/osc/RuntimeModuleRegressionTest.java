package com.kukuqi.tvbox.osc;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.test.InstrumentationTestCase;
import com.kukuqi.tvbox.engine.runtime.RuntimeModule;
import com.kukuqi.tvbox.engine.runtime.RuntimeModules;
import com.kukuqi.tvbox.osc.util.RuntimeDownload;
import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Separate APK loading and untrusted/partial download regressions, using a local HTTP fixture. */
public class RuntimeModuleRegressionTest extends InstrumentationTestCase {
    private Context host;
    private NanoHTTPD server;
    private String base;
    private volatile boolean corruptHash, truncate;
    private volatile String requestedRange;
    private final java.util.concurrent.atomic.AtomicInteger apkRequests = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger catalogRequests = new java.util.concurrent.atomic.AtomicInteger();
    /** 目录声明必须与素材 APK 完全一致；-1 表示从素材读取真实 versionCode。 */
    private int catalogVersion = -1;
    private boolean productionCatalog;
    private volatile boolean corruptNode;
    @Override protected void setUp() throws Exception {
        super.setUp(); host = getInstrumentation().getTargetContext();
        for (RuntimeModule module : RuntimeModule.values()) assertTrue("缺少回归素材：" + fixture(module), fixture(module).isFile());
        File[] staged = new File(host.getCacheDir(), "runtime-downloads").listFiles();
        if (staged != null) for (File file : staged) assertTrue(file.delete());
        server = new NanoHTTPD("127.0.0.1", 0) {
            @Override public Response serve(IHTTPSession session) {
                try {
                    if (session.getUri().equals("/catalog.json")) {
                        catalogRequests.incrementAndGet();
                        return newFixedLengthResponse(Response.Status.OK, "application/json", catalog().toString());
                    }
                    if (session.getUri().startsWith("/bundle/")) {
                        String path = session.getUri();
                        String value = path.contains("index.config.js") ? "module.exports={};" : nodeBundle();
                        if (path.endsWith(".md5")) value = corruptNode ? "0".repeat(32) : com.github.catvod.utils.Crypto.md5(value);
                        if (path.endsWith(".json")) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "missing");
                        return newFixedLengthResponse(Response.Status.OK, "text/javascript", value);
                    }
                    RuntimeModule requested = java.util.Arrays.stream(RuntimeModule.values()).filter(module -> session.getUri().equals("/" + module.id + ".apk")).findFirst().orElseThrow();
                    File file = fixture(requested);
                    apkRequests.incrementAndGet();
                    if (truncate) return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", new FileInputStream(file), 131072);
                    requestedRange = session.getHeaders().get("range");
                    if (requestedRange != null) {
                        long offset = Long.parseLong(requestedRange.substring(6).replace("-", ""));
                        FileInputStream input = new FileInputStream(file); input.skipNBytes(offset);
                        Response response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, "application/octet-stream", input, file.length() - offset);
                        response.addHeader("Content-Range", "bytes " + offset + "-" + (file.length() - 1) + "/" + file.length());
                        return response;
                    }
                    return newFixedLengthResponse(Response.Status.OK, "application/vnd.android.package-archive", new FileInputStream(file), file.length());
                } catch (Exception e) { return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.toString()); }
            }
        };
        server.start(); base = "http://127.0.0.1:" + server.getListeningPort();
    }
    @Override protected void tearDown() throws Exception {
        server.stop();
        // 用例间必须隔离：卸载/降级过的运行时模块在结束时装回，避免污染后续用例。
        reinstallAllModules();
        super.tearDown();
    }
    private File fixture(RuntimeModule module) { return new File("/sdcard/Download/tvbox-runtime-fixtures/" + module.id + ".apk"); }
    private File[] partials(RuntimeModule module) {
        File[] files = new File(host.getCacheDir(), "runtime-downloads").listFiles((dir, name) -> name.startsWith(module.id + "-") && name.endsWith(".part"));
        return files == null ? new File[0] : files;
    }
    /** 读取素材 APK 的真实 versionCode，避免目录声明与产物失配。 */
    private int fixtureVersionCode(RuntimeModule module) {
        android.content.pm.PackageInfo info = host.getPackageManager().getPackageArchiveInfo(
                fixture(module).getAbsolutePath(), 0);
        if (info == null) throw new IllegalStateException("回归素材不可读: " + module.id);
        return info.versionCode;
    }
    /** 目录版本必须来自实际素材，避免模块升级后误报不兼容。 */
    private String fixtureVersionName(RuntimeModule module) {
        PackageInfo info = host.getPackageManager().getPackageArchiveInfo(fixture(module).getAbsolutePath(), 0);
        if (info == null) throw new IllegalStateException("回归素材不可读: " + module.id);
        return info.versionName;
    }
    private JSONObject catalog() throws Exception {
        JSONArray modules = new JSONArray();
        for (RuntimeModule module : RuntimeModule.values()) {
            File file = fixture(module);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream in = new FileInputStream(file)) { byte[] b = new byte[32768]; int n; while ((n = in.read(b)) != -1) digest.update(b, 0, n); }
            StringBuilder sha = new StringBuilder(); for (byte b : digest.digest()) sha.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            JSONObject artifact = new JSONObject().put("abi", "arm64-v8a").put("minSdk", 24).put("size", file.length())
                    .put("sha256", corruptHash ? "0".repeat(64) : sha.toString()).put("url", base + "/" + module.id + ".apk");
            modules.put(new JSONObject().put("id", module.id).put("packageName", productionCatalog ? "com.kukuqi.tvbox.runtime." + module.id : module.packageName(host)).put("compatibilityId", module.contract())
                    .put("versionCode", catalogVersion > 0 ? catalogVersion : fixtureVersionCode(module)).put("versionName", fixtureVersionName(module)).put("artifacts", new JSONArray().put(artifact)));
        }
        return new JSONObject().put("schema", 1).put("modules", modules);
    }
    /** 让指定模块处于"未安装"状态，使下载路径可被真实触发。 */
    private void uninstall(RuntimeModule module) throws Exception {
        try (android.os.ParcelFileDescriptor result = getInstrumentation().getUiAutomation()
                .executeShellCommand("pm uninstall " + module.packageName(host));
             java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(result)) {
            input.readAllBytes();
        }
    }
    /** 该用例断言"模块未安装"的行为，必须先卸掉设备上可能已安装的运行时模块。 */
    private void uninstallAllModules() throws Exception {
        for (RuntimeModule module : RuntimeModule.values()) {
            try (android.os.ParcelFileDescriptor result = getInstrumentation().getUiAutomation()
                    .executeShellCommand("pm uninstall " + module.packageName(host));
                 java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(result)) {
                input.readAllBytes();
            }
        }
    }
    public void testZz1MissingModulesDoNotInitializeAtStartup() throws Exception {
        assertNotNull(com.kukuqi.tvbox.engine.api.loader.BaseLoader.get());
        assertNotNull(com.kukuqi.tvbox.engine.player.extractor.Source.get());
        com.kukuqi.tvbox.engine.api.loader.BaseLoader.get().clear();
        com.kukuqi.tvbox.engine.player.extractor.Source.get().stop();
        uninstallAllModules();
        try {
            for (RuntimeModule module : RuntimeModule.values()) {
                // 只断言卸载后的可观测结果：状态回落到未安装，且借模块建对象会被拒绝。
                // 不断言 isLoaded()：同一进程内先前用例加载过的 ClassLoader 不会因 APK 卸载而消失，
                // 这是 DexClassLoader 的固有行为，"启动时不自动初始化"由状态断言覆盖。
                assertEquals("未安装 · 点击下载", RuntimeModules.status(host, module));
                try { RuntimeModules.create(host, module); fail("Missing runtime was accepted"); }
                catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("模块管理")); }
            }
        } finally {
            // 复原模块安装状态。注意：重装会让进程内已缓存的 ClassLoader 与磁盘版本不一致，
            // 界面会如实显示"已更新 · 重启应用生效"，这是模块热替换不可行的正确表现，
            // 因此依赖"已安装"文案的用例必须排在本用例之前（见 testZz* 命名约定）。
            reinstallAllModules();
        }
    }
    /** 用回归素材把运行时模块装回设备，供依赖"已安装"状态的用例使用。 */
    private void reinstallAllModules() throws Exception {
        for (RuntimeModule module : RuntimeModule.values()) {
            try {
                if (RuntimeModules.installed(host, module) != null) continue;
            } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
                // 未安装，继续执行安装。
            }
            try (android.os.ParcelFileDescriptor result = getInstrumentation().getUiAutomation()
                    .executeShellCommand("pm install -r " + fixture(module).getAbsolutePath());
                 java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(result)) {
                input.readAllBytes();
            }
        }
    }
    public void testZz4DownloadsVerifyAllModuleApks() throws Exception {
        uninstallAllModules();
        for (RuntimeModule module : RuntimeModule.values()) {
            File apk = RuntimeDownload.download(host, module, base + "/catalog.json", value -> {});
            assertEquals(fixture(module).length(), apk.length());
            assertEquals(0, partials(module).length);
        }
    }
    public void testZz3CorruptDownloadRejectedAndCanRetry() throws Exception {
        uninstallAllModules();
        corruptHash = true;
        try { RuntimeDownload.download(host, RuntimeModule.QUICKJS, base + "/catalog.json", value -> {}); fail("Corrupt module accepted"); }
        catch (SecurityException expected) { assertTrue(expected.getMessage().contains("SHA-256")); }
        assertEquals(0, partials(RuntimeModule.QUICKJS).length);
        corruptHash = false;
        assertTrue(RuntimeDownload.download(host, RuntimeModule.QUICKJS, base + "/catalog.json", value -> {}).isFile());
    }
    public void testVerifiedCachedApkIsReusedButTamperingRedownloads() throws Exception {
        uninstallAllModules();
        File apk = RuntimeDownload.download(host, RuntimeModule.QUICKJS, base + "/catalog.json", value -> {});
        assertEquals(1, apkRequests.get());
        assertEquals(apk, RuntimeDownload.download(host, RuntimeModule.QUICKJS, base + "/catalog.json", value -> {}));
        assertEquals("Retry must reuse fully verified APK", 1, apkRequests.get());
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(apk, "rw")) { file.seek(100); int value = file.read(); file.seek(100); file.write(value ^ 255); }
        assertNotNull(RuntimeDownload.download(host, RuntimeModule.QUICKJS, base + "/catalog.json", value -> {}));
        assertEquals("Tampered cache must be replaced", 2, apkRequests.get());
    }
    public void testInstalledCurrentOrNewerModulesDoNotFetchApks() throws Exception {
        assertNotNull(RuntimeModules.installed(host, RuntimeModule.QUICKJS));
        // 目录版本低于真机已装版本时必须跳过下载（低版本不重复拉包）。
        catalogVersion = 1;
        assertNull(RuntimeDownload.download(host, RuntimeModule.QUICKJS, base + "/catalog.json", value -> {}));
        assertNull("重复检查也不应重新下载", RuntimeDownload.download(host, RuntimeModule.QUICKJS, base + "/catalog.json", value -> {}));
        assertEquals(0, apkRequests.get());
    }
    public void testProductionCatalogExplainsValidationPackageMismatch() throws Exception {
        // 该保护只在独立验证版（包名以 .corevalidation 结尾）生效，正式包必须跳过而不是误判通过。
        if (!host.getPackageName().endsWith(".corevalidation")) return; // 仅独立验证版构建适用
        productionCatalog = true;
        try { RuntimeDownload.download(host, RuntimeModule.PYTHON, base + "/catalog.json", value -> {}); fail("Production catalog accepted by validation app"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("独立验证版")); }
        assertEquals(0, apkRequests.get());
    }
    public void testNewerCatalogStillDownloadsAndRejectsWrongVersion() throws Exception {
        assertNotNull(RuntimeModules.installed(host, RuntimeModule.QUICKJS));
        // 目录声明高出版本，但素材 APK 实际版本不符，必须在校验阶段被拒。
        catalogVersion = fixtureVersionCode(RuntimeModule.QUICKJS) + 1;
        try { RuntimeDownload.download(host, RuntimeModule.QUICKJS, base + "/catalog.json", value -> {}); fail("Wrong version accepted"); }
        catch (SecurityException expected) { assertTrue(expected.getMessage().contains("版本")); }
        assertEquals(1, apkRequests.get());
    }
    public void testInstalledRowDoesNotDownloadAndExplicitCheckOnlyFetchesCatalog() throws Exception {
        getInstrumentation().runOnMainSync(() -> com.kukuqi.tvbox.osc.base.App.getInstance().isNormalStart = true);
        android.app.Activity screen = getInstrumentation().startActivitySync(new android.content.Intent(host, com.kukuqi.tvbox.osc.ui.activity.MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            getInstrumentation().runOnMainSync(() -> com.kukuqi.tvbox.osc.ui.dialog.FeatureSettingsDialog.modules(screen, () -> base + "/catalog.json"));
            clickText("QuickJS ·");
            Thread.sleep(300);
            assertEquals("Installed row must not access network", 0, catalogRequests.get());
            assertEquals(0, apkRequests.get());
            clickText("检查更新");
            for (int i = 0; i < 100 && catalogRequests.get() == 0; i++) Thread.sleep(100);
            assertEquals(1, catalogRequests.get());
            boolean ready = false;
            for (int i = 0; i < 150 && !ready; i++) {
                android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
                ready = root != null && !root.findAccessibilityNodeInfosByText("QuickJS · 已安装 " + fixtureVersionName(RuntimeModule.QUICKJS)).isEmpty();
                if (!ready) Thread.sleep(100);
            }
            assertTrue("Explicit version check did not finish and refresh installed status", ready);
            assertEquals(0, apkRequests.get());
            try (java.io.FileOutputStream image = new java.io.FileOutputStream("/sdcard/Download/tvbox-module308-manager.png")) {
                getInstrumentation().getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, image);
            }
        } finally { getInstrumentation().runOnMainSync(screen::finish); }
    }
    private void clickText(String text) throws Exception {
        for (int i = 0; i < 100; i++) {
            android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                if (node.isClickable() && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return;
            }
            Thread.sleep(100);
        }
        fail("Could not click " + text);
    }
    public void testZz5IncompleteDownloadRejected() throws Exception {
        uninstallAllModules();
        truncate = true;
        try { RuntimeDownload.download(host, RuntimeModule.PYTHON, base + "/catalog.json", value -> {}); fail("Truncated module accepted"); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("不完整")); }
        assertEquals(1, partials(RuntimeModule.PYTHON).length);
        assertEquals(131072, partials(RuntimeModule.PYTHON)[0].length());
    }
    public void testZz6InterruptedModuleDownloadResumesAndVerifies() throws Exception {
        uninstallAllModules();
        truncate = true;
        try { RuntimeDownload.download(host, RuntimeModule.QUICKJS, base + "/catalog.json", value -> {}); fail("Incomplete module accepted"); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("不完整")); }
        File[] partial = partials(RuntimeModule.QUICKJS);
        assertNotNull(partial); assertEquals(1, partial.length); assertEquals(131072, partial[0].length());
        truncate = false;
        File apk = RuntimeDownload.download(host, RuntimeModule.QUICKJS, base + "/catalog.json", value -> {});
        assertEquals("bytes=131072-", requestedRange);
        assertEquals(fixture(RuntimeModule.QUICKJS).length(), apk.length());
        assertFalse(partial[0].exists());
    }
    @SuppressWarnings("deprecation")
    public void testForeignSignerAndContractRejected() throws Exception {
        PackageInfo info = host.getPackageManager().getPackageArchiveInfo(fixture(RuntimeModule.QUICKJS).getAbsolutePath(), PackageManager.GET_META_DATA | PackageManager.GET_SIGNATURES);
        assertNotNull(info);
        info.signatures = new android.content.pm.Signature[]{new android.content.pm.Signature("01020304")};
        try { RuntimeModules.verify(host, RuntimeModule.QUICKJS, info); fail("Foreign signer accepted"); }
        catch (SecurityException expected) { assertTrue(expected.getMessage().contains("签名")); }
        info.applicationInfo.metaData.putString("tvbox.runtime.contract", "other-api");
        try { RuntimeModules.verify(host, RuntimeModule.QUICKJS, info); fail("Foreign contract accepted"); }
        catch (SecurityException expected) { assertTrue(expected.getMessage().contains("接口")); }
    }
    public void testZz2ModulePageDownloadsAndOffersVerifiedApkToInstaller() throws Exception {
        // 点击"下载"必须真的走下载路径：先确保 Python 模块未安装。
        uninstall(RuntimeModule.PYTHON);
        android.app.Activity activity = null;
        android.content.IntentFilter filter = new android.content.IntentFilter(android.content.Intent.ACTION_VIEW);
        filter.addDataType("application/vnd.android.package-archive"); filter.addDataScheme("content");
        android.app.Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(filter,
                new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null), true);
        try {
            getInstrumentation().runOnMainSync(() -> {
                com.kukuqi.tvbox.osc.base.App.getInstance().isNormalStart = true;
            });
            activity = getInstrumentation().startActivitySync(new android.content.Intent(host, com.kukuqi.tvbox.osc.ui.activity.MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(com.kukuqi.tvbox.osc.ui.activity.MainActivity.EXTRA_START_DESTINATION, R.id.navigation_dashboard));
            android.app.Activity screen = activity;
            getInstrumentation().runOnMainSync(() -> com.kukuqi.tvbox.osc.ui.dialog.FeatureSettingsDialog.modules(screen, () -> base + "/catalog.json"));
            boolean clicked = false;
            for (int i = 0; i < 100 && !clicked; i++) {
                android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if (root != null) for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("Python ·")) {
                    if (node.isClickable()) { clicked = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK); break; }
                }
                if (!clicked) Thread.sleep(100);
            }
            assertTrue("Module download row could not be clicked", clicked);
            for (int i = 0; i < 200 && monitor.getHits() == 0; i++) Thread.sleep(100);
            assertEquals("Verified APK was not offered to Android installer", 1, monitor.getHits());
            File apk = new File(host.getCacheDir(), "runtime-downloads/python.apk");
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(host, host.getPackageName() + ".fileprovider", apk);
            try (java.io.InputStream in = host.getContentResolver().openInputStream(uri)) { assertNotNull(in); assertEquals('P', in.read()); assertEquals('K', in.read()); }
            clickText("Python ·");
            for (int i = 0; i < 100 && monitor.getHits() < 2; i++) Thread.sleep(100);
            assertEquals(2, monitor.getHits());
            assertEquals("Cancelled installer must not download APK again", 1, apkRequests.get());
            try (android.os.ParcelFileDescriptor result = getInstrumentation().getUiAutomation().executeShellCommand("pm install -r " + fixture(RuntimeModule.PYTHON).getAbsolutePath());
                 java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(result)) {
                assertTrue(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("Success"));
            }
            getInstrumentation().runOnMainSync(() -> screen.startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + host.getPackageName()))));
            getInstrumentation().waitForIdleSync();
            Thread.sleep(500);
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            for (int i = 0; i < 100; i++) {
                android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
                // 安装成功后界面必须由"点击下载"变为已装态。若本进程先前加载过旧模块，
                // 应用会如实提示"已更新 · 重启应用生效"，两种都是"安装已生效"的正确表示。
                if (root != null && (!root.findAccessibilityNodeInfosByText("Python · 已安装 " + fixtureVersionName(RuntimeModule.PYTHON)).isEmpty()
                        || !root.findAccessibilityNodeInfosByText("Python · 已更新 · 重启应用生效").isEmpty())) break;
                if (i == 99) {
                    StringBuilder seen = new StringBuilder();
                    android.view.accessibility.AccessibilityNodeInfo cur = getInstrumentation().getUiAutomation().getRootInActiveWindow();
                    if (cur != null) for (android.view.accessibility.AccessibilityNodeInfo n : cur.findAccessibilityNodeInfosByText("Python")) seen.append('[').append(n.getText()).append(']');
                    fail("Installer return did not refresh installed status; seen=" + seen);
                }
                Thread.sleep(100);
            }
            clickText("Python ·");
            Thread.sleep(300);
            assertEquals(1, apkRequests.get());
        } finally {
            getInstrumentation().removeMonitor(monitor);
            android.app.Activity screen = activity;
            getInstrumentation().runOnMainSync(() -> {
                if (screen != null) screen.finish();
            });
        }
    }
    public void testInstalledModulesShareHostApiAndLoadNativeThunder() throws Exception {
        for (RuntimeModule module : RuntimeModule.values()) {
            assertEquals("已安装 " + fixtureVersionName(module), RuntimeModules.status(host, module));
            assertTrue(RuntimeModules.installed(host, module).applicationInfo.icon != 0);
            assertNotNull(host.getPackageManager().getApplicationIcon(module.packageName(host)));
            ClassLoader loader = RuntimeModules.loader(host, module);
            assertSame(com.github.catvod.crawler.Spider.class, loader.loadClass("com.github.catvod.crawler.Spider"));
            assertNotSame(host.getClassLoader(), loader.loadClass(module.entry).getClassLoader());
        }
        // BD-J's Files.copy is absent from the shrunk host, but present in its own APK.
        ClassLoader bdj = RuntimeModules.loader(host, RuntimeModule.BDJ);
        assertSame(bdj, bdj.loadClass("j$.nio.file.Files").getClassLoader());
        // A local torrent exercises native Thunder parsing without starting a network download.
        File torrent = new File(host.getCacheDir(), "runtime-fixture.torrent");
        // Thunder intentionally filters files <= 30 MiB. Only metadata is generated, no video payload.
        java.nio.file.Files.writeString(torrent.toPath(), "d4:infod6:lengthi33554432e4:name9:video.mp412:piece lengthi33554432e6:pieces20:01234567890123456789ee");
        Object thunder = RuntimeModules.create(host, RuntimeModule.THUNDER);
        List<?> media = (List<?>) RuntimeModules.call(thunder, "parse", new Class<?>[]{String.class}, "file://" + torrent.getAbsolutePath());
        assertEquals(1, media.size());
        RuntimeModules.call(thunder, "exit", new Class<?>[0]);
    }
    private String nodeBundle() {
        return """
                const {createHash}=require('node:crypto');
                const http=require('node:http');
                let ext='';
                async function message(action,opt={}) {
                  return new Promise((resolve,reject)=>{
                    const body=JSON.stringify({action,opt});
                    const req=http.request({host:'127.0.0.1',port:global.catDartServerPort(),path:'/msg',method:'POST'},res=>{
                      let value='';res.on('data',chunk=>value+=chunk);res.on('end',()=>resolve(JSON.parse(value)));
                    });req.on('error',reject);req.end(body);
                  });
                }
                module.exports={async start(){
                  const os=await import('node:os');
                  global.catServerFactory(async(req,res)=>{
                    try {
                      let input='';for await(const chunk of req)input+=chunk;
                      const body=input?JSON.parse(input):{};
                      const path=req.url.split('?')[0];
                      res.setHeader('Content-Type','application/json');
                      let result={};
                      if(path==='/config')result={video:{sites:[{key:'nodejs_fixture',name:'fixture',api:'/fixture',type:3}]}};
                      else if(path==='/fixture/init')ext=body.ext;
                      else if(path==='/fixture/home')result={class:[{type_id:'1',type_name:ext}],node:process.versions.node,arch:os.arch(),hash:createHash('sha256').update('ok').digest('hex'),host:await message('getUserAgent')};
                      else if(path==='/fixture/category')result={list:[{vod_id:body.id,vod_name:body.page+body.filters.q}]};
                      else if(path==='/fixture/detail')result={list:[{vod_id:body.id}]};
                      else if(path==='/fixture/search')result={list:[{vod_name:body.wd+body.page}]};
                      else if(path==='/fixture/play')result={parse:0,url:body.id,info:await message('getPlayInfo')};
                      else if(path==='/fixture/proxy'){res.setHeader('Content-Type','application/octet-stream');return res.end(Buffer.from([0,255,65]));}
                      else {res.statusCode=404;}
                      res.end(JSON.stringify(result));
                    }catch(error){res.statusCode=500;res.end(JSON.stringify({error:String(error)}));}
                  }).listen(0);
                }};
                """;
    }
    public void testLegacyCacheCleanupPreservesUserFiles() throws Exception {
        File oldCache = new File(host.getCodeCacheDir(), "runtimes/quickjs/old/payload.so");
        assertTrue(oldCache.getParentFile().mkdirs() || oldCache.getParentFile().isDirectory());
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(oldCache)) { output.write("obsolete".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        File userFile = new File(host.getFilesDir(), "runtime-user-source.py");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(userFile)) { output.write("user source".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        try {
            RuntimeModules.cleanLegacyCaches(host);
            assertFalse(oldCache.exists());
            try (java.io.FileInputStream input = new java.io.FileInputStream(userFile)) {
                byte[] content = new byte[64];
                int length = input.read(content);
                assertEquals("user source", new String(content, 0, length, java.nio.charset.StandardCharsets.UTF_8));
            }
        } finally { userFile.delete(); }
    }
    public void testNodeSourcesExecuteCommonJsEsmAndBinaryProxy() throws Exception {
        com.kukuqi.tvbox.engine.api.loader.BaseLoader loader = new com.kukuqi.tvbox.engine.api.loader.BaseLoader();
        com.kukuqi.tvbox.engine.runtime.NodeRuntime runtime = com.kukuqi.tvbox.engine.runtime.NodeRuntime.get();
        try {
            JSONObject config = new JSONObject(runtime.loadConfig(base + "/bundle/index.js.md5"));
            assertEquals("node:/fixture", config.getJSONArray("sites").getJSONObject(0).getString("api"));
            com.github.catvod.crawler.Spider spider = loader.getSpider("node-validation", "node:/fixture", "node-ready", "");
            JSONObject home = new JSONObject(spider.homeContent(true));
            assertEquals("node-ready", home.getJSONArray("class").getJSONObject(0).getString("type_name"));
            assertTrue(home.getString("node").startsWith("24."));
            assertFalse(home.getString("arch").isEmpty());
            assertEquals(64, home.getString("hash").length());
            assertTrue(home.getJSONObject("host").getBoolean("success"));
            assertFalse(home.getJSONObject("host").getString("ua").isEmpty());
            assertEquals("2value", new JSONObject(spider.categoryContent("a", "2", true, new java.util.HashMap<>(java.util.Map.of("q", "value")))).getJSONArray("list").getJSONObject(0).getString("vod_name"));
            assertEquals("id", new JSONObject(spider.detailContent(java.util.List.of("id"))).getJSONArray("list").getJSONObject(0).getString("vod_id"));
            assertEquals("keyword3", new JSONObject(spider.searchContent("keyword", false, "3")).getJSONArray("list").getJSONObject(0).getString("vod_name"));
            JSONObject play = new JSONObject(spider.playerContent("fixture", "https://example.org/video.mp4", java.util.List.of()));
            assertEquals("https://example.org/video.mp4", play.getString("url"));
            assertEquals("fixture", play.getJSONObject("info").getString("flag"));
            assertEquals("https://example.org/video.mp4", play.getJSONObject("info").getString("id"));
            loader.setRecent("node-validation", "node:/fixture", "");
            Object[] proxy = loader.proxy(new java.util.HashMap<>(java.util.Map.of("do", "nodejs")));
            assertEquals(200, proxy[0]);
            try (java.io.InputStream input = (java.io.InputStream) proxy[2]) { assertEquals(0,input.read());assertEquals(255,input.read());assertEquals(65,input.read()); }
            loader.clear(); runtime.clear();
            runtime.loadConfig(base + "/bundle/index.js.md5");
            assertTrue(loader.getSpider("node-restart", "node:/fixture", "restarted", "").homeContent(false).contains("restarted"));
            loader.clear(); runtime.clear(); corruptNode = true;
            try { runtime.loadConfig(base + "/bundle/index.js.md5"); fail("错误校验和不得启动 bundle"); }
            catch (java.io.IOException expected) {}
        } finally { loader.clear(); runtime.clear(); }
    }
    public void testBdjRuntimePreparesJreAndLoadsJvm() throws Exception {
        String home = com.kukuqi.tvbox.engine.runtime.BdjSupport.prepare();
        assertTrue(new File(home, "lib/rt.jar").isFile());
        assertTrue(new File(home, "lib/aarch64/server/libjvm.so").isFile());
        assertEquals(home, com.kukuqi.tvbox.engine.runtime.BdjSupport.prepare());
        assertTrue(new File(new File(home).getParentFile(), "libbluray-j2se-1.4.1.jar").isFile());
    }
    public void testBdjJvmExecutesJavaInDisposableProcess() throws Exception {
        String home = com.kukuqi.tvbox.engine.runtime.BdjSupport.prepare();
        Object runtime = RuntimeModules.create(host, RuntimeModule.BDJ);
        String version = (String) RuntimeModules.call(runtime, "probe", new Class<?>[]{String.class, String.class}, home,
                new File(new File(home).getParentFile(), "libbluray-j2se-1.4.1.jar").getAbsolutePath());
        assertTrue("Unexpected JRE version: " + version, version.startsWith("1.8.0_"));
    }
    public void testCatalogMigrationClearsFixtureAndKeepsHttps() throws Exception {
        String previous = com.orhanobut.hawk.Hawk.get(RuntimeDownload.CATALOG_KEY, "");
        try {
            com.orhanobut.hawk.Hawk.put(RuntimeDownload.CATALOG_KEY, "http://127.0.0.1:43199/catalog.json");
            RuntimeDownload.migrateCatalog();
            assertEquals(BuildConfig.RUNTIME_CATALOG_URL, RuntimeDownload.catalogUrl());
            com.orhanobut.hawk.Hawk.put(RuntimeDownload.CATALOG_KEY, "https://example.org/catalog.json");
            RuntimeDownload.migrateCatalog();
            assertEquals("https://example.org/catalog.json", RuntimeDownload.catalogUrl());
        } finally {
            if (previous.isEmpty() || previous.startsWith("http://127.0.0.1:")) com.orhanobut.hawk.Hawk.delete(RuntimeDownload.CATALOG_KEY);
            else com.orhanobut.hawk.Hawk.put(RuntimeDownload.CATALOG_KEY, previous);
        }
    }
}
