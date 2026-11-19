package com.kukuqi.quickjs.crawler;

import com.kukuqi.quickjs.QuickJS;
import com.kukuqi.quickjs.QuickJSLoader;
import com.kukuqi.quickjs.host.Host;
import com.kukuqi.quickjs.utils.Module;

import dalvik.system.DexClassLoader;

public class Loader {

    public Loader() {
        QuickJSLoader.init();
    }

    public static void check() {
        try (QuickJS context = new QuickJS(QuickJSLoader::init)) {
            if (((Number) context.evaluate("6 * 7").join()).intValue() != 42) throw new IllegalStateException("QuickJS evaluation returned an unexpected result");
        }
    }

    public Spider spider(String api, DexClassLoader dex) {
        return spider(api, dex, Host.NONE);
    }

    public Spider spider(String api, DexClassLoader dex, Host host) {
        return new Spider(api, dex, host);
    }

    public Spider spider(String api, DexClassLoader dex, Host host, Module module) {
        return new Spider(api, dex, host, module);
    }
}
