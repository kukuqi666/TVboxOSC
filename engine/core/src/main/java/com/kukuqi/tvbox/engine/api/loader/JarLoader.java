package com.kukuqi.tvbox.engine.api.loader;

import android.content.Context;

import com.kukuqi.tvbox.engine.App;
import com.github.catvod.net.Download;
import com.kukuqi.tvbox.engine.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

public class JarLoader extends SpiderLoader {

    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, Method> methods;
    private final ConcurrentHashMap<String, Object> locks;
    private volatile String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
    }

    @Override
    protected void clearReferences() {
        loaders.clear();
        methods.clear();
        locks.clear();
        recent = null;
    }

    public synchronized void setRecent(String recent) {
        checkOpen();
        this.recent = recent;
    }

    private void load(String key, File file) {
        if (Thread.interrupted()) return;
        if (!Path.exists(file) || !file.setReadOnly()) return;
        String cachePath = Path.jar().getAbsolutePath();
        DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, com.github.catvod.utils.RuntimeModule.getSpiderClassLoader());
        invokeInit(loader);
        synchronized (this) {
            checkOpen();
            invokeProxy(key, loader);
            loaders.put(key, loader);
        }
    }

    private void invokeInit(DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("init", Context.class);
            method.invoke(clz, App.get());
        } catch (ClassNotFoundException | NoSuchMethodException optional) {
            // 部分合法 JAR 没有全局初始化入口。
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("JAR 全局初始化失败", failure);
        }
    }

    private void invokeProxy(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void parseJar(String key, String jar) {
        checkOpen();
        if (loaders.containsKey(key)) return;
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            checkOpen();
            if (loaders.containsKey(key)) return;
            String[] texts = jar.split(";md5;");
            String md5 = texts.length > 1 ? texts[1].trim() : "";
            if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
            jar = texts[0];
            if (!md5.isEmpty() && Crypto.equals(Path.jar(jar), md5)) {
                load(key, Path.jar(jar));
            } else if (jar.startsWith("http")) {
                File downloaded = Download.create(jar, Path.jar(jar)).get();
                verifyChecksum(downloaded, md5);
                load(key, downloaded);
            } else if (jar.startsWith("file")) {
                File local = Path.local(jar);
                verifyChecksum(local, md5);
                load(key, local);
            } else if (!jar.isEmpty() && new java.io.File(jar).isFile()) {
                // 测试台与调试场景允许直接给出本机绝对路径，例如 /sdcard/demo.jar
                File local = Path.local(jar);
                verifyChecksum(local, md5);
                load(key, local);
            }
        }
    }

    private void verifyChecksum(File file, String md5) {
        if (!md5.isEmpty() && !Crypto.equals(file, md5)) throw new IllegalStateException("JAR MD5 校验失败");
    }

    public DexClassLoader dex(String jar) {
        if (jar == null || jar.isEmpty()) return null;
        try {
            String jaKey = Crypto.md5(jar);
            parseJar(jaKey, jar);
            return loaders.get(jaKey);
        } catch (java.util.concurrent.CancellationException cancelled) {
            throw cancelled;
        } catch (Throwable e) {
            throw new IllegalStateException("JAR 加载失败：" + jar, e);
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        String jaKey = Crypto.md5(jar);
        return load(jaKey + '\u0000' + key, App.get(), ext, () -> {
            parseJar(jaKey, jar);
            DexClassLoader loader = loaders.get(jaKey);
            if (loader == null) throw new IllegalStateException("JAR 未载入：" + jar);
            try {
                Spider spider = (Spider) loader.loadClass("com.github.catvod.spider." + api.substring(4)).getDeclaredConstructor().newInstance();
                spider.prepare(key);
                return spider;
            } catch (ClassNotFoundException absent) {
                // 保持现有 UI 对失效站点类名的降级行为，初始化失败则明确报错并允许重试。
                return new SpiderNull();
            }
        });
    }

    private DexClassLoader requireRecentLoader() {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) throw new IllegalStateException("No jar loaded for recent key: " + recent);
        return loader;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Json" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
        return (JSONObject) method.invoke(null, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Mix" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
        return (JSONObject) method.invoke(null, jxs, name, flag, url);
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Method method = recent != null ? methods.get(recent) : null;
        Object[] result = proxyInvoke(method, params);
        if (result != null) return result;
        return tryOthers(params);
    }

    private Object[] tryOthers(Map<String, String> p) {
        return methods.entrySet().stream().filter(e -> !e.getKey().equals(recent)).map(e -> proxyInvoke(e.getValue(), p)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Object[] proxyInvoke(Method method, Map<String, String> params) {
        try {
            return method == null ? null : (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
