package com.kukuqi.tvbox.engine.api.loader;

import com.kukuqi.tvbox.engine.App;
import com.kukuqi.tvbox.engine.runtime.RuntimeModule;
import com.kukuqi.tvbox.engine.runtime.RuntimeModules;
import com.github.catvod.crawler.Spider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsLoader {

    private static volatile com.kukuqi.quickjs.host.Host host = com.kukuqi.quickjs.host.Host.NONE;

    public static void setHost(com.kukuqi.quickjs.host.Host value) {
        host = value == null ? com.kukuqi.quickjs.host.Host.NONE : value;
    }

    public static com.kukuqi.quickjs.host.Host host() { return host; }

    private final ConcurrentHashMap<String, Spider> spiders;
    private com.github.catvod.runtime.QuickJsRuntime.Session loader;
    private final JarLoader jarLoader;
    private volatile String recent;

    public JsLoader() { this(new JarLoader()); }

    public JsLoader(JarLoader jarLoader) {
        this.jarLoader = jarLoader;
        spiders = new ConcurrentHashMap<>();
    }

    public void clear() {
        spiders.values().forEach(spider -> { try { spider.destroy(); } catch (Throwable error) { error.printStackTrace(); } finally { spider.releaseNetwork(); } });
        if (loader != null) loader.clear();
        loader = null;
        spiders.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        RuntimeModules.loader(App.get(), RuntimeModule.QUICKJS);
        if (loader == null) loader = com.github.catvod.utils.RuntimeModule.QUICKJS.entry(com.github.catvod.runtime.QuickJsRuntime.class).session();
        return spiders.computeIfAbsent(key, k -> {
            Spider spider = null;
            try {
                spider = loader.spider(api, jarLoader.dex(jar), host);
                spider.prepare(key);
                spider.init(App.get(), ext);
                return spider;
            } catch (Throwable e) {
                if (spider != null) try { spider.destroy(); } catch (Throwable ignored) {} finally { spider.releaseNetwork(); }
                e.printStackTrace();
                throw new IllegalStateException("JavaScript 来源初始化失败，可重试", e);
            }
        });
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
