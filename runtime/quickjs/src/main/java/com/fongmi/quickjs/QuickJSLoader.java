package com.fongmi.quickjs;

import android.content.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class QuickJSLoader {

    public static final String COMPATIBILITY_ID = "fongmi-quickjs-ng-0.17-v13";
    private static Context context;
    private static boolean loaded;

    private QuickJSLoader() {
    }

    /** 上下文由宿主完成包名、签名和模块契约校验后传入。 */
    public static synchronized void configure(Context value) {
        context = value;
    }

    public static synchronized void init() {
        if (context == null) throw new IllegalStateException("QuickJS 模块尚未初始化");
        if (loaded) return;
        // 当前类来自模块的 DexClassLoader，由它定位安装后提取的原生库。
        System.loadLibrary("quickjs-wrapper");
        loaded = true;
    }

    public static String readAsset(String name) {
        String path = name.replace("assets://", "");
        if (!path.startsWith("js/") || path.contains("..") || path.contains("\\"))
            throw new IllegalArgumentException("非法 QuickJS 资源路径");
        Context source;
        synchronized (QuickJSLoader.class) { source = context; }
        if (source == null) throw new IllegalStateException("QuickJS 模块尚未初始化");
        try (InputStream input = source.getAssets().open(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("读取 QuickJS 模块资源失败：" + path, error);
        }
    }
}
