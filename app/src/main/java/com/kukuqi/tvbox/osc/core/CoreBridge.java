package com.kukuqi.tvbox.osc.core;

import android.os.Handler;
import android.os.Looper;
import com.kukuqi.tvbox.engine.api.SiteApi;
import com.kukuqi.tvbox.engine.api.config.VodConfig;
import com.kukuqi.tvbox.engine.api.loader.BaseLoader;
import com.kukuqi.tvbox.engine.bean.Result;
import com.kukuqi.tvbox.engine.bean.Site;
import com.github.catvod.crawler.Spider;
import com.google.gson.JsonObject;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.bean.SourceBean;
import com.kukuqi.tvbox.osc.ui.dialog.VideoLinkDialog;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps TVboxOSC preferences and presentation models while delegating source execution to the core engine. */
public final class CoreBridge {
    private static final ExecutorService CONFIG = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.atomic.AtomicInteger CONFIG_GENERATION = new java.util.concurrent.atomic.AtomicInteger();
    private CoreBridge() {}

    /** 由宿主接入网页、Cookie 和 UA，独立运行时通过共享 Host 接口调用。 */
    public static void installScriptHost() {
        com.kukuqi.tvbox.engine.api.loader.JsLoader.setHost(new com.fongmi.quickjs.host.Host() {
            @Override public long getCallTimeout() {
                return com.kukuqi.tvbox.engine.Constant.TIMEOUT_VOD;
            }
            @Override public boolean openWeb(String options) {
                try {
                    JsonObject web = com.google.gson.JsonParser.parseString(options).getAsJsonObject();
                    if (!web.has("url") || !web.get("url").isJsonPrimitive()
                            || !WebCookiePolicy.http(web.get("url").getAsString())) return false;
                    MAIN.post(() -> {
                        try {
                            com.kukuqi.tvbox.engine.App.get().startActivity(
                                    new android.content.Intent(com.kukuqi.tvbox.engine.App.get(),
                                            com.kukuqi.tvbox.osc.ui.activity.SourceWebActivity.class)
                                            .putExtra("web", web.toString())
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (RuntimeException error) {
                            com.kukuqi.tvbox.osc.util.LOG.e("脚本网页打开失败", error);
                        }
                    });
                    return true;
                } catch (RuntimeException invalid) { return false; }
            }
            @Override public String getCookie(String url) {
                try {
                    if (!WebCookiePolicy.http(url)) return "";
                    String value = android.webkit.CookieManager.getInstance().getCookie(url);
                    return value == null ? "" : value;
                } catch (RuntimeException error) { return ""; }
            }
            @Override public boolean setCookie(String url, String value) {
                try {
                    if (!WebCookiePolicy.http(url) || value == null
                            || okhttp3.Cookie.parse(okhttp3.HttpUrl.get(url), value) == null) return false;
                    android.webkit.CookieManager.getInstance().setCookie(url, value);
                    android.webkit.CookieManager.getInstance().flush();
                    return true;
                } catch (RuntimeException error) { return false; }
            }
            @Override public String getUserAgent() {
                try {
                    String value = android.webkit.WebSettings.getDefaultUserAgent(com.kukuqi.tvbox.engine.App.get());
                    return value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 ? "" : value;
                } catch (RuntimeException error) { return ""; }
            }
            @Override public java.util.concurrent.CompletableFuture<String> dialog(String options) {
                return com.github.catvod.Runtime.dialog(options, null);
            }
            @Override public java.util.concurrent.CompletableFuture<String> dialog(String options,
                    java.util.function.Function<String, java.util.concurrent.CompletableFuture<String>> callback) {
                return com.github.catvod.Runtime.dialog(options, callback);
            }
        });
    }

    public static void loadConfig(String url, String home, JsonObject document, ApiConfig.LoadConfigCallback callback) {
        int generation = CONFIG_GENERATION.incrementAndGet();
        JsonObject snapshot = document == null ? null : document.deepCopy();
        CONFIG.execute(() -> {
            if (generation != CONFIG_GENERATION.get()) return;
            try {
                if (snapshot == null) throw new IllegalStateException("没有可加载的点播配置");
                VodConfig.get().accept(url, home, snapshot);
                selectParse(Hawk.get(HawkConfig.DEFAULT_PARSE, ""));
                MAIN.post(() -> { if (generation == CONFIG_GENERATION.get()) callback.success(); });
            } catch (Throwable failure) {
                android.util.Log.e("TVboxCore", "Configuration failed", failure);
                MAIN.post(() -> { if (generation == CONFIG_GENERATION.get()) callback.error("核心配置加载失败：" + failure.getMessage()); });
            }
        });
    }

    public static Site site(String key) {
        Site site = VodConfig.get().getSite(key);
        if (!site.isEmpty()) return site;
        SourceBean source = ApiConfig.get().getSource(key);
        if (source == null) return new Site();
        JsonObject object = new JsonObject();
        object.addProperty("key", source.getKey());
        object.addProperty("name", source.getName());
        object.addProperty("type", source.getType());
        object.addProperty("api", source.getApi());
        object.addProperty("ext", source.getExt());
        object.addProperty("jar", source.getJar());
        object.addProperty("playUrl", source.getPlayerUrl());
        object.add("header", com.kukuqi.tvbox.engine.App.gson().toJsonTree(source.getHeaders()));
        return Site.objectFrom(object, ApiConfig.get().getSpider());
    }

    public static Spider spider(SourceBean source) {
        Site site = site(source.getKey());
        // Live plugins can be temporary and are not part of VodConfig's site collection.
        if (site.isEmpty()) {
            String jar = source.getJar().isEmpty() ? ApiConfig.get().getSpider() : source.getJar();
            BaseLoader.get().setRecent(source.getKey(), source.getApi(), jar);
            return BaseLoader.get().getSpider(source.getKey(), source.getApi(), source.getExt(), jar);
        }
        return site.recent().spider();
    }

    public static Result player(String key, String flag, String url) throws Exception {
        if (VideoLinkDialog.SOURCE.equals(key)) {
            JsonObject object = new JsonObject();
            object.addProperty("url", url);
            object.addProperty("key", key);
            object.addProperty("flag", flag);
            object.addProperty("parse", com.kukuqi.tvbox.osc.util.DefaultConfig.isVideoFormat(url) ? 0 : 1);
            object.addProperty("jx", VodConfig.hasParse() ? 1 : 0);
            return Result.objectFrom(object.toString());
        }
        return SourceAccess.call(key, () -> SiteApi.playerContent(key, flag, url));
    }

    public static void selectHome(String key) {
        Site site = VodConfig.get().getSite(key);
        if (!site.isEmpty()) VodConfig.get().setHome(site);
    }

    public static Result preload(String key, String flag, String url) throws Exception {
        if (VideoLinkDialog.SOURCE.equals(key)) return player(key, flag, url);
        return SourceAccess.call(key, () -> SiteApi.preloadContent(key, flag, url));
    }

    public static void selectParse(String name) {
        VodConfig.get().getParses().stream().filter(parse -> parse.getName().equals(name)).findFirst().ifPresent(VodConfig.get()::setParse);
    }
}
