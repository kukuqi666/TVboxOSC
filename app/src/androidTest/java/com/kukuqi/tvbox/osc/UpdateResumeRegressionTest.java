package com.kukuqi.tvbox.osc;

import android.content.Context;
import android.test.InstrumentationTestCase;
import androidx.work.Data;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestWorkerBuilder;
import com.kukuqi.tvbox.osc.update.UpdateStore;
import com.kukuqi.tvbox.osc.update.UpdateWorker;
import com.kukuqi.tvbox.osc.util.UpdateManifest;
import fi.iki.elonen.NanoHTTPD;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

/** Actual HTTP Range, file persistence, APK/package/version and matching signing certificate verification. */
public class UpdateResumeRegressionTest extends InstrumentationTestCase {
    private UpdateStore store;
    private NanoHTTPD server;
    private ExecutorService executor;
    private byte[] payload;
    private volatile boolean truncate;
    private volatile String range;
    private volatile int requests;
    private static String base;
    @Override protected void setUp() throws Exception {
        super.setUp();
        store = new UpdateStore(getInstrumentation().getTargetContext());
        try (FileInputStream in = new FileInputStream("/sdcard/Download/tvbox-update-fixtures/update.apk")) { payload = in.readAllBytes(); }
        assertTrue(payload.length > 131072);
        StringBuilder sha = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(payload)) sha.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        JSONObject manifest = new JSONObject().put("version", "3.1.2").put("version_code", BuildConfig.VERSION_CODE + 1)
                .put("package_name", BuildConfig.APPLICATION_ID).put("sha256", sha.toString()).put("size", payload.length)
                .put("apk_url", "https://github.com/kukuqi666/TVboxOSC/releases/download/v3.1.2/TVboxOSC-v3.1.2.apk");
        store.prefs.edit().clear().putBoolean("auto_download", false).putString("manifest", manifest.toString()).commit();
        store.partial(store.manifest()).delete(); store.apk(store.manifest()).delete();
        executor = Executors.newSingleThreadExecutor();
        server = new NanoHTTPD("127.0.0.1", 0) {
            @Override public Response serve(IHTTPSession session) {
                requests++; range = session.getHeaders().get("range");
                if (truncate) return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", new ByteArrayInputStream(Arrays.copyOf(payload, 131072)), 131072);
                int offset = range == null ? 0 : Integer.parseInt(range.substring(6).replace("-", ""));
                Response response = newFixedLengthResponse(offset == 0 ? Response.Status.OK : Response.Status.PARTIAL_CONTENT,
                        "application/octet-stream", new ByteArrayInputStream(payload, offset, payload.length - offset), payload.length - offset);
                if (offset > 0) response.addHeader("Content-Range", "bytes " + offset + "-" + (payload.length - 1) + "/" + payload.length);
                return response;
            }
        };
        server.start(); base = "http://127.0.0.1:" + server.getListeningPort();
    }
    @Override protected void tearDown() throws Exception {
        server.stop(); executor.shutdownNow();
        store.partial(store.manifest()).delete(); store.apk(store.manifest()).delete();
        store.prefs.edit().clear().commit();
        super.tearDown();
    }
    private LocalWorker worker() {
        return TestWorkerBuilder.from(getInstrumentation().getTargetContext(), LocalWorker.class, executor)
                .setInputData(new Data.Builder().putBoolean("download", true).putBoolean("manual", true).build()).build();
    }
    public void testActualHttpResumeBecomesReadyOnlyAfterArchiveAndSignatureVerification() throws Exception {
        truncate = true;
        worker().doWork();
        assertEquals(UpdateStore.ERROR, store.state());
        assertEquals(131072L, store.partial(store.manifest()).length());
        assertTrue(store.prefs.getString("message", "").contains("保留进度"));
        assertFalse(store.hasReadyUpdate());
        truncate = false;
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker().doWork());
        assertEquals("bytes=131072-", range);
        assertEquals(2, requests);
        assertTrue(store.hasReadyUpdate());
        assertFalse(store.partial(store.manifest()).exists());
        store.manifest().verifyDownload(store.apk(store.manifest()));
    }
    public void testCompletePartialIsVerifiedWithoutDownloadingAgain() throws Exception {
        File partial = store.partial(store.manifest()); partial.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(partial)) { out.write(payload); }
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker().doWork());
        assertEquals(0, requests);
        assertTrue(store.hasReadyUpdate());
        assertFalse(partial.exists());
    }
    public static class LocalWorker extends UpdateWorker {
        private final OkHttpClient transport = new OkHttpClient();
        public LocalWorker(Context context, WorkerParameters parameters) { super(context, parameters); }
        @Override protected List<String> downloadRoutes(UpdateManifest manifest) { return List.of(manifest.apkUrl); }
        @Override protected Response requestDownload(Request request) throws IOException {
            return transport.newCall(request.newBuilder().url(base + "/app.apk").build()).execute();
        }
    }
}
