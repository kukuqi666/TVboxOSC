package com.kukuqi.tvbox.engine.quickjs.crawler;

import com.kukuqi.quickjs.host.Host;
import com.kukuqi.quickjs.utils.Module;
import com.github.catvod.crawler.Spider;

import dalvik.system.DexClassLoader;

/** 唯一模块入口，兼容宿主反射调用与共享 SDK 的隔离会话。 */
public class Loader implements com.github.catvod.runtime.QuickJsRuntime {

    private final com.kukuqi.quickjs.crawler.Loader delegate;
    private final Module module;

    public Loader(android.content.Context context) {
        com.kukuqi.quickjs.QuickJSLoader.configure(context);
        this.delegate = new com.kukuqi.quickjs.crawler.Loader();
        this.module = new Module();
    }

    public void clear() {
        if (module != null) module.clear();
    }

    public Spider spider(String api, DexClassLoader dex) {
        return delegate.spider(api, dex, Host.NONE, module);
    }

    public Spider spider(String api, DexClassLoader dex, Host host) {
        return delegate.spider(api, dex, host == null ? Host.NONE : host, module);
    }
    @Override public void check() { com.kukuqi.quickjs.crawler.Loader.check(); }
    @Override public java.util.concurrent.CompletableFuture<String> evaluate(String source, String options) {
        return com.kukuqi.quickjs.utils.Scripts.evaluate(source, options);
    }
    @Override public Session session() {
        Module isolated = new Module();
        return new Session() {
            @Override public Spider spider(String api, DexClassLoader dex, Host host) { return delegate.spider(api, dex, host, isolated); }
            @Override public void clear() { isolated.clear(); }
        };
    }
}