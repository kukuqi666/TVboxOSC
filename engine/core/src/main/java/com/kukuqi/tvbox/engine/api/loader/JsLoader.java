package com.kukuqi.tvbox.engine.api.loader;

import com.kukuqi.tvbox.engine.App;
import com.kukuqi.tvbox.engine.runtime.RuntimeModule;
import com.kukuqi.tvbox.engine.runtime.RuntimeModules;
import com.github.catvod.crawler.Spider;

import java.util.Map;

public class JsLoader extends SpiderLoader {

    private static volatile com.kukuqi.quickjs.host.Host host = com.kukuqi.quickjs.host.Host.NONE;

    public static void setHost(com.kukuqi.quickjs.host.Host value) {
        host = value == null ? com.kukuqi.quickjs.host.Host.NONE : value;
    }

    public static com.kukuqi.quickjs.host.Host host() { return host; }

    private com.github.catvod.runtime.QuickJsRuntime.Session loader;
    private final JarLoader jarLoader;
    private volatile String recent;

    public JsLoader() { this(new JarLoader()); }

    public JsLoader(JarLoader jarLoader) {
        this.jarLoader = jarLoader;
    }

    @Override
    protected synchronized void clearReferences() {
        if (loader != null) loader.clear();
        loader = null;
        recent = null;
    }

    public synchronized void setRecent(String recent) {
        checkOpen();
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        return load(key, App.get(), ext, () -> {
            RuntimeModules.loader(App.get(), RuntimeModule.QUICKJS);
            com.github.catvod.runtime.QuickJsRuntime.Session session;
            synchronized (this) {
                checkOpen();
                if (loader == null) loader = com.github.catvod.utils.RuntimeModule.QUICKJS.entry(com.github.catvod.runtime.QuickJsRuntime.class).session();
                session = loader;
            }
            Spider spider = session.spider(api, jarLoader.dex(jar), host);
            spider.prepare(key);
            return spider;
        });
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = findSpider(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
