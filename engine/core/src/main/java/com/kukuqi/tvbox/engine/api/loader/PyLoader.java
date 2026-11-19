package com.kukuqi.tvbox.engine.api.loader;

import com.kukuqi.tvbox.engine.App;
import com.kukuqi.tvbox.engine.runtime.RuntimeModule;
import com.kukuqi.tvbox.engine.runtime.RuntimeModules;
import com.github.catvod.crawler.Spider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private Object loader;
    private volatile String recent;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
    }

    public void clear() {
        spiders.values().forEach(spider -> { try { spider.destroy(); } catch (Throwable error) { error.printStackTrace(); } finally { spider.releaseNetwork(); } });
        spiders.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        RuntimeModules.loader(App.get(), RuntimeModule.PYTHON);
        if (loader == null) loader = RuntimeModules.create(App.get(), RuntimeModule.PYTHON);
        return spiders.computeIfAbsent(key, k -> {
            Spider spider = null;
            try {
                spider = (Spider) RuntimeModules.call(loader, "spider", new Class<?>[]{String.class, String.class, com.github.catvod.net.Net.Cookies.class, java.util.function.Supplier.class}, api, key, JsLoader.host(), (java.util.function.Supplier<String>) JsLoader.host()::getUserAgent);
                spider.init(App.get(), ext);
                return spider;
            } catch (Throwable e) {
                if (spider != null) try { spider.destroy(); } catch (Throwable ignored) {} finally { spider.releaseNetwork(); }
                e.printStackTrace();
                throw new IllegalStateException("Python 来源初始化失败，可重试", e);
            }
        });
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
