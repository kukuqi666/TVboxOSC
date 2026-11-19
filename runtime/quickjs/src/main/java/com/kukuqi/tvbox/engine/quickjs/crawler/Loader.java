package com.kukuqi.tvbox.engine.quickjs.crawler;

import com.fongmi.quickjs.host.Host;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.crawler.Spider;

import dalvik.system.DexClassLoader;

/**
 * 宿主反射入口桥接（契约由 engine/core 的 RuntimeModules 固定）：
 *   Loader(Context) / clear() / spider(String api, DexClassLoader dex)
 *
 * <p>实现在 FongMi 的 com.fongmi.quickjs.crawler.Loader（含 catvod.js / net.js / dialog.js /
 * spider.js 完整模块体系），本类只做签名适配，不复制逻辑。</p>
 */
public class Loader {

    private final com.fongmi.quickjs.crawler.Loader delegate;
    private Module module;

    public Loader(android.content.Context context) {
        com.fongmi.quickjs.QuickJSLoader.configure(context);
        this.delegate = new com.fongmi.quickjs.crawler.Loader();
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
}