package com.kukuqi.tvbox.osc;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.Surface;

import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.kukuqi.tvbox.engine.api.loader.BaseLoader;
import com.kukuqi.tvbox.engine.bean.Result;
import com.kukuqi.tvbox.engine.player.exo.ExoPlayerEngine;
import com.kukuqi.tvbox.engine.player.media.PlaySpec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 使用实际发布配置验证动态类、来源接口和播放器首帧；外网失败写入结果，不伪装成播放通过。 */
public class SourceCompatibilityRegressionTest extends InstrumentationTestCase {
    private static final File INPUT = new File("/sdcard/Download/tvbox-source-validation/cases.json");
    private File output;
    private JSONArray results;
    private JSONObject current;
    private ExecutorService worker;
    private long deadline;

    public void testEncryptedJarSources() throws Exception { runCases("encrypted"); }
    public void testPackagedPythonSources() throws Exception { runCases("python"); }
    public void testLiveSubscriptions() throws Exception { runCases("subscriptions"); }

    public void testPythonDependencyMatrix() throws Exception {
        File source = new File(getInstrumentation().getTargetContext().getCacheDir(), "dependency-matrix.py");
        String code = """
            from base.spider import Spider as Base
            class Spider(Base):
             def homeContent(self, filter):
              import bz2, lzma, zlib, sqlite3, ssl, ujson, requests, urllib3, certifi, cachetools, cffi, numpy, pywasm
              from bs4 import BeautifulSoup
              from pyquery import PyQuery
              from charset_normalizer import from_bytes
              from Crypto.Cipher import AES, ChaCha20_Poly1305
              from Crypto.PublicKey import RSA
              from cryptography.hazmat.primitives.ciphers.aead import AESGCM
              from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
              from lxml import etree
              assert ujson.loads(ujson.dumps({'n':42}))['n']==42
              assert BeautifulSoup('<b>kukuqi</b>','html.parser').b.text=='kukuqi'
              assert PyQuery('<b>kukuqi</b>')('b').text()=='kukuqi'
              assert etree.fromstring(b'<a><b>42</b></a>').xpath('string(b)')=='42'
              assert str(from_bytes(b'kukuqi').best())=='kukuqi'
              for codec in (bz2,lzma,zlib): assert codec.decompress(codec.compress(b'kukuqi'))==b'kukuqi'
              db=sqlite3.connect(':memory:'); assert db.execute('select 6*7').fetchone()[0]==42; db.close()
              assert ssl.OPENSSL_VERSION and certifi.where()
              assert requests.Request('GET','https://example.com',params={'a':42}).prepare().url.endswith('a=42')
              cache=cachetools.TTLCache(2,30); cache['n']=42; assert cache['n']==42
              assert cffi.FFI().sizeof('long long')==8
              assert numpy.allclose(numpy.linalg.inv(numpy.array([[2.,0.],[0.,4.]])),[[.5,0.],[0.,.25]])
              aes=AES.new(bytes(16),AES.MODE_ECB); assert aes.decrypt(aes.encrypt(bytes(16)))==bytes(16)
              cipher=ChaCha20_Poly1305.new(key=bytes(32),nonce=bytes(12)); encrypted,tag=cipher.encrypt_and_digest(b'kukuqi')
              assert ChaCha20_Poly1305.new(key=bytes(32),nonce=bytes(12)).decrypt_and_verify(encrypted,tag)==b'kukuqi'
              assert RSA.generate(1024).has_private()
              gcm=AESGCM(bytes(16)); assert gcm.decrypt(bytes(12),gcm.encrypt(bytes(12),b'kukuqi',None),None)==b'kukuqi'
              key=Ed25519PrivateKey.generate(); key.public_key().verify(key.sign(b'kukuqi'),b'kukuqi')
              self.setCache('legacy-test', {'n':42}, 60)
              assert self.getCache('legacy-test')['n']==42
              self.delCache('legacy-test'); assert self.getCache('legacy-test') is None
              assert self.regStr('/movie/42.html',r'/movie/([0-9]+)')=='42'
              assert self.html(self.cleanText('<b>42</b>')).xpath('string(//b)')=='42'
              response=self.fetch('http://127.0.0.1:18080/build/source-validation/cases.json')
              assert response.status_code==200 and len(response.json()['python'])>0
              return {'class':[{'type_id':'1','type_name':'dependency-matrix-ok'}]}
            """;
        try (FileOutputStream stream = new FileOutputStream(source)) { stream.write(code.getBytes(StandardCharsets.UTF_8)); }
        BaseLoader loader = new BaseLoader();
        try { assertTrue(loader.getSpider("dependency-matrix", "file://" + source.getAbsolutePath(), "", "").homeContent(false).contains("dependency-matrix-ok")); }
        finally { loader.destroy(); source.delete(); }
    }

    private void runCases(String group) throws Exception {
        assertTrue("先使用 scripts/verify_source_compatibility.py prepare 准备素材", INPUT.isFile());
        JSONObject corpus = new JSONObject(read(INPUT));
        JSONArray cases = corpus.getJSONArray(group);
        output = new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), "source-validation/" + group + ".json");
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        results = new JSONArray();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject source = cases.getJSONObject(i);
            current = new JSONObject().put("id", source.getString("id")).put("file", source.optString("file"))
                    .put("key", source.optString("key")).put("api", source.optString("display_api", source.optString("api")))
                    .put("jar", source.optString("display_jar")).put("stage", "init").put("status", "running");
            results.put(current); save();
            long start = SystemClock.elapsedRealtime();
            deadline = start + 55000;
            worker = Executors.newSingleThreadExecutor(runnable -> { Thread thread = new Thread(runnable, "source-validation"); thread.setDaemon(true); return thread; });
            BaseLoader loader = BaseLoader.get();
            try {
                if (group.equals("subscriptions")) probeSubscription(source, loader);
                else probe(source, loader);
            } catch (Throwable error) {
                current.put("status", error instanceof java.util.concurrent.TimeoutException ? "timeout" : "failed")
                        .put("error", describe(error));
            } finally {
                worker.shutdownNow();
                // 第三方 destroy 不保证响应中断，沿用宿主的后台销毁机制。
                try { loader.destroy(); } catch (Throwable error) { current.put("cleanup_error", describe(error)); }
                current.put("duration_ms", SystemClock.elapsedRealtime() - start);
                save();
                Bundle progress = new Bundle();
                progress.putString("stream", "\nSOURCE " + group + " " + (i + 1) + "/" + cases.length() + " " + current.toString() + "\n");
                getInstrumentation().sendStatus(0, progress);
            }
        }
        assertEquals("必须为每个输入保留结果", cases.length(), results.length());
    }

    private void probeSubscription(JSONObject source, BaseLoader loader) throws Exception {
        current.put("stage", "configuration");
        String url = source.getString("api");
        String raw = call(() -> com.kukuqi.tvbox.engine.api.Decoder.getJson(url, "source-validation"));
        JSONObject config = new JSONObject(raw);
        JSONArray sites = config.optJSONArray("sites");
        if (sites == null || sites.length() == 0) throw new IllegalStateException("订阅没有点播来源");
        current.put("site_count", sites.length());
        // 用真实配置中的普通点播来源执行全链路，不挑本地伪造站点。
        JSONArray children = new JSONArray();
        for (int i = 0; i < sites.length() && children.length() < 3; i++) {
            JSONObject site = sites.getJSONObject(i);
            String api = site.optString("api"), key = site.optString("key");
            if (site.optInt("type") != 3 || !api.startsWith("csp_")) continue;
            if (!(api + key).matches("(?i).*(NewCz|Czsapp|Apple|Jpys|Libvio|JPJ|Bttwoo|Anime|Auete|App99).*")) continue;
            JSONObject child = new JSONObject().put("id", source.getString("id") + "/" + key).put("key", key)
                    .put("api", api).put("ext", site.opt("ext") == null ? "" : site.get("ext"))
                    .put("jar", site.optString("jar", config.optString("spider")));
            JSONObject parent = current;
            current = new JSONObject().put("key", key).put("api", api).put("stage", "init");
            deadline = SystemClock.elapsedRealtime() + 55000;
            BaseLoader childLoader = BaseLoader.get();
            ExecutorService parentWorker = worker;
            worker = Executors.newSingleThreadExecutor(runnable -> { Thread thread = new Thread(runnable, "subscription-validation"); thread.setDaemon(true); return thread; });
            try { probe(child, childLoader); }
            catch (Throwable error) { current.put("status", "failed").put("error", describe(error)); }
            finally { worker.shutdownNow(); worker = parentWorker; childLoader.destroy(); children.put(current); current = parent; }
        }
        current.put("sources", children).put("status", "configuration_loaded");
    }

    private void probe(JSONObject source, BaseLoader loader) throws Exception {
        JSONObject entry = new JSONObject(source.toString());
        entry.put("key", source.getString("id")).put("type", 3);
        JSONObject config = new JSONObject().put("sites", new JSONArray().put(entry));
        com.kukuqi.tvbox.engine.api.config.VodConfig.get().accept("http://127.0.0.1:18080/validation.json", "source-validation",
                com.google.gson.JsonParser.parseString(config.toString()).getAsJsonObject());
        Object ext = source.opt("ext");
        String extend = ext == null || ext == JSONObject.NULL ? "" : ext.toString();
        if (source.getString("api").startsWith("csp_")) {
            Class<?> resolved = call(() -> loader.dex(source.getString("jar"))
                    .loadClass("com.github.catvod.spider." + source.getString("api").substring(4)));
            if (!Spider.class.isAssignableFrom(resolved)) throw new ClassCastException("来源没有继承共享 Spider");
            current.put("class_resolved", true); save();
        }
        Spider spider = call(() -> loader.getSpider(source.getString("id"), source.getString("api"), extend, source.optString("jar")));
        if (spider instanceof SpiderNull) throw new ClassNotFoundException("JAR 未提供 " + source.getString("api"));
        current.put("class_loaded", true).put("stage", "home"); save();
        String homeValue = call(() -> spider.homeContent(false));
        JSONObject home = homeValue == null || homeValue.isEmpty() ? new JSONObject() : json(homeValue);
        current.put("home_valid", true).put("class_count", size(home.optJSONArray("class")));
        JSONArray videos = home.optJSONArray("list");
        if (videos == null || videos.length() == 0) {
            current.put("stage", "home_video");
            videos = json(call(spider::homeVideoContent)).optJSONArray("list");
        }
        JSONArray categories = home.optJSONArray("class");
        if ((videos == null || videos.length() == 0) && categories != null && categories.length() > 0) {
            current.put("stage", "category"); save();
            String tid = categories.getJSONObject(0).optString("type_id");
            videos = json(call(() -> spider.categoryContent(tid, "1", false, new HashMap<>()))).optJSONArray("list");
        }
        if (videos == null || videos.length() == 0) {
            current.put("stage", "search"); save();
            videos = json(call(() -> spider.searchContent("三体", false))).optJSONArray("list");
        }
        if (videos == null || videos.length() == 0) {
            current.put("status", "loaded_no_items"); return;
        }
        current.put("list_count", videos.length()).put("stage", "detail"); save();
        String id = videos.getJSONObject(0).optString("vod_id");
        JSONArray details = json(call(() -> spider.detailContent(List.of(id)))).optJSONArray("list");
        if (details == null || details.length() == 0) { current.put("status", "empty_detail"); return; }
        JSONObject detail = details.getJSONObject(0);
        String[] flags = detail.optString("vod_play_from").split("\\$\\$\\$");
        String[] groups = detail.optString("vod_play_url").split("\\$\\$\\$");
        if (groups.length == 0 || groups[0].isEmpty()) { current.put("status", "no_episodes"); return; }
        String episode = groups[0].split("#")[0];
        int dollar = episode.indexOf('$');
        String playId = dollar < 0 ? episode : episode.substring(dollar + 1);
        current.put("stage", "player"); save();
        JSONObject play = json(call(() -> spider.playerContent(flags.length == 0 ? "" : flags[0], playId, Collections.emptyList())));
        if (play.optString("url").isEmpty()) { current.put("status", "empty_player_url"); return; }
        current.put("player_url_present", true).put("parse", play.optInt("parse")).put("jx", play.optInt("jx"));
        if (play.optInt("parse") != 0 || play.optInt("jx") != 0) { current.put("status", "requires_parser"); return; }
        if (play.optString("url").matches("(?s)^(magnet|ed2k|thunder):.*")) {
            current.put("status", "requires_thunder"); return;
        }
        current.put("stage", "decode"); save();
        verifyPlayback(play.toString());
        current.put("status", current.optBoolean("audio_progress") ? "audio_verified" : "playback_verified");
    }

    private void verifyPlayback(String value) throws Exception {
        ExoPlayerEngine[] engine = {null}; SurfaceTexture[] texture = {null}; Surface[] surface = {null};
        AtomicBoolean frame = new AtomicBoolean(), audioOnly = new AtomicBoolean(); AtomicLong position = new AtomicLong();
        AtomicReference<PlaybackException> failure = new AtomicReference<>();
        try {
            getInstrumentation().runOnMainSync(() -> {
                texture[0] = new SurfaceTexture(0); surface[0] = new Surface(texture[0]);
                engine[0] = new ExoPlayerEngine(1, new Player.Listener() {
                    @Override public void onPlayerError(PlaybackException error) { failure.set(error); }
                    @Override public void onRenderedFirstFrame() { frame.set(true); }
                });
                engine[0].getPlayer().setVideoSurface(surface[0]);
                engine[0].start(PlaySpec.from(Result.fromJson(value), "来源兼容验证", MediaMetadata.EMPTY), 0);
            });
            long until = SystemClock.elapsedRealtime() + 15000;
            while (SystemClock.elapsedRealtime() < until) {
                if (failure.get() != null) throw failure.get();
                getInstrumentation().runOnMainSync(() -> {
                    Player player = engine[0].getPlayer();
                    position.set(player.getCurrentPosition());
                    audioOnly.set(player.isPlaying() && player.getCurrentTracks().isTypeSelected(androidx.media3.common.C.TRACK_TYPE_AUDIO)
                            && !player.getCurrentTracks().isTypeSelected(androidx.media3.common.C.TRACK_TYPE_VIDEO));
                });
                if (audioOnly.get() && position.get() > 500) {
                    current.put("audio_progress", true).put("position_ms", position.get()); return;
                }
                if (frame.get() && position.get() > 500) {
                    current.put("first_frame", true).put("position_ms", position.get()); return;
                }
                Thread.sleep(150);
            }
            throw new java.util.concurrent.TimeoutException("15 秒内未得到视频首帧与播放进度");
        } finally {
            getInstrumentation().runOnMainSync(() -> { if (engine[0] != null) engine[0].release(); if (surface[0] != null) surface[0].release(); if (texture[0] != null) texture[0].release(); });
        }
    }

    private <T> T call(Callable<T> action) throws Exception {
        long remaining = Math.min(12000, deadline - SystemClock.elapsedRealtime());
        if (remaining <= 0) throw new java.util.concurrent.TimeoutException("来源验证总时限已到");
        Future<T> future = worker.submit(action);
        try { return future.get(remaining, TimeUnit.MILLISECONDS); }
        catch (ExecutionException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof Exception error) throw error;
            if (cause instanceof Error error) throw error;
            throw wrapped;
        } finally { if (!future.isDone()) future.cancel(true); }
    }
    private static JSONObject json(String value) throws Exception {
        if (value == null || value.isEmpty()) throw new IllegalStateException("来源返回空响应");
        Object parsed = new org.json.JSONTokener(value).nextValue();
        return parsed instanceof JSONArray list ? new JSONObject().put("list", list) : (JSONObject) parsed;
    }
    private static int size(JSONArray array) { return array == null ? 0 : array.length(); }
    private static String read(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }
    private void save() throws Exception {
        File staging = new File(output.getPath() + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(staging)) {
            stream.write(results.toString(2).getBytes(StandardCharsets.UTF_8)); stream.getFD().sync();
        }
        if (!staging.renameTo(output)) throw new java.io.IOException("无法写入验证结果");
    }
    private static String describe(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; error != null && i < 6; i++, error = error.getCause()) {
            if (i > 0) text.append(" <- ");
            text.append(error.getClass().getSimpleName()).append(": ").append(error.getMessage());
        }
        return text.substring(0, Math.min(text.length(), 1600));
    }
}
