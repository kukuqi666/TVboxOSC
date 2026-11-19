package com.kukuqi.tvbox.engine.api.loader;

import com.kukuqi.tvbox.engine.App;
import com.kukuqi.tvbox.engine.runtime.RuntimeModule;
import com.kukuqi.tvbox.engine.runtime.RuntimeModules;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsLoader {

    private static volatile com.fongmi.quickjs.host.Host host = com.fongmi.quickjs.host.Host.NONE;

    public static void setHost(com.fongmi.quickjs.host.Host value) {
        host = value == null ? com.fongmi.quickjs.host.Host.NONE : value;
    }

    private final ConcurrentHashMap<String, Spider> spiders;
    private Object loader;
    private volatile String recent;

    public JsLoader() {
        spiders = new ConcurrentHashMap<>();
    }

    public void clear() {
        spiders.values().forEach(spider -> { try { spider.destroy(); } catch (Throwable error) { error.printStackTrace(); } finally { spider.releaseNetwork(); } });
        if (loader != null) try { RuntimeModules.call(loader, "clear", new Class<?>[0]); } catch (Exception e) { e.printStackTrace(); }
        spiders.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        RuntimeModules.loader(App.get(), RuntimeModule.QUICKJS);
        if (loader == null) loader = RuntimeModules.create(App.get(), RuntimeModule.QUICKJS);
        return spiders.computeIfAbsent(key, k -> {
            Spider spider = null;
            try {
                spider = (Spider) RuntimeModules.call(loader, "spider", new Class<?>[]{String.class, dalvik.system.DexClassLoader.class, com.fongmi.quickjs.host.Host.class}, api, BaseLoader.get().dex(jar), host);
                spider.prepare(key);
                spider.init(App.get(), ext);
                return spider;
            } catch (Throwable e) {
                if (spider != null) try { spider.destroy(); } catch (Throwable ignored) {} finally { spider.releaseNetwork(); }
                try { RuntimeModules.call(loader, "clear", new Class<?>[0]); } catch (Exception ignored) {}
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
