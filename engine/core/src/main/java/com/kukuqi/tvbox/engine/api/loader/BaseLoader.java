package com.kukuqi.tvbox.engine.api.loader;

import android.text.TextUtils;

import com.kukuqi.tvbox.engine.api.config.LiveConfig;
import com.kukuqi.tvbox.engine.api.config.VodConfig;
import com.kukuqi.tvbox.engine.bean.Live;
import com.kukuqi.tvbox.engine.bean.Site;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Crypto;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import com.kukuqi.tvbox.engine.utils.Task;

import dalvik.system.DexClassLoader;

public class BaseLoader {

    private volatile JarLoader jarLoader;
    private volatile PyLoader pyLoader;
    private volatile JsLoader jsLoader;
    private volatile NodeLoader nodeLoader;
    private final List<SpiderLoader> retiredLoaders = new ArrayList<>();

    public BaseLoader() {
        jarLoader = new JarLoader();
        nodeLoader = new NodeLoader();
    }

    public static BaseLoader get() {
        return Loader.INSTANCE;
    }

    private static boolean isJs(String api) {
        return api.contains(".js");
    }

    private static boolean isPy(String api) {
        return api.contains(".py");
    }

    private static boolean isCsp(String api) {
        return api.startsWith("csp_");
    }

    private synchronized JsLoader js() {
        if (jsLoader == null) jsLoader = new JsLoader(jarLoader);
        return jsLoader;
    }

    private synchronized PyLoader python() {
        if (pyLoader == null) pyLoader = new PyLoader();
        return pyLoader;
    }

    public void clear() { rotate(false); }
    public void destroy() { rotate(true); }

    private void rotate(boolean exiting) {
        List<SpiderLoader> previous = new ArrayList<>();
        List<SpiderLoader> retired;
        synchronized (this) {
            previous.add(jarLoader);
            previous.add(nodeLoader);
            if (pyLoader != null) previous.add(pyLoader);
            if (jsLoader != null) previous.add(jsLoader);
            retiredLoaders.addAll(previous);
            retired = exiting ? new ArrayList<>(retiredLoaders) : previous;
            jarLoader = new JarLoader();
            nodeLoader = new NodeLoader();
            pyLoader = null;
            jsLoader = null;
        }
        // 先关闭旧代，阻止迟到的初始化进入缓存，再在后台释放原生资源。
        Throwable failure = null;
        for (SpiderLoader loader : previous) {
            try { loader.clear(); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else if (failure != error) failure.addSuppressed(error);
            }
        }
        Task.execute(() -> {
            for (SpiderLoader loader : retired) {
                try { loader.destroy(); }
                catch (RuntimeException | Error error) { com.github.catvod.crawler.SpiderDebug.log(error); }
                finally { synchronized (BaseLoader.this) { retiredLoaders.remove(loader); } }
            }
        });
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        if (NodeLoader.accepts(api)) return nodeLoader.getSpider(key, api, ext);
        else if (isPy(api)) return python().getSpider(key, api, ext);
        else if (isJs(api)) return js().getSpider(key, api, ext, jar);
        else if (isCsp(api)) return jarLoader.getSpider(key, api, ext, jar);
        else return new SpiderNull();
    }

    public Spider getSpider(String key) {
        Site site = VodConfig.get().getSite(key);
        Live live = LiveConfig.get().getLive(key);
        if (!site.isEmpty()) return site.spider();
        if (!live.isEmpty()) return live.spider();
        return new SpiderNull();
    }

    public void setRecent(String key, String api, String jar) {
        if (NodeLoader.accepts(api)) nodeLoader.setRecent(key, api);
        else if (isJs(api)) js().setRecent(key);
        else if (isPy(api)) python().setRecent(key);
        else if (isCsp(api)) jarLoader.setRecent(Crypto.md5(jar));
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (params.containsKey("siteKey")) return getSpider(params.get("siteKey")).proxy(params);
        if ("js".equals(params.get("do"))) return js().proxy(params);
        if ("nodejs".equals(params.get("do")) || "node".equals(params.get("do"))) return nodeLoader.proxy(params);
        if ("py".equals(params.get("do"))) return python().proxy(params);
        return jarLoader.proxy(params);
    }

    public void parseJar(String jar, boolean recent) {
        if (TextUtils.isEmpty(jar)) return;
        String key = Crypto.md5(jar);
        JarLoader loader = jarLoader;
        loader.parseJar(key, jar);
        if (recent) loader.setRecent(key);
    }

    public DexClassLoader dex(String jar) {
        return jarLoader.dex(jar);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    private static class Loader {
        static volatile BaseLoader INSTANCE = new BaseLoader();
    }
}
