package com.github.catvod.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import com.github.catvod.Runtime;
import com.github.catvod.runtime.RuntimePlugin;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/** 脚本 SDK 与宿主共享同一套签名校验和模块加载器，避免重复提取或混用 APK。 */
public enum RuntimeModule {
    PYTHON("python", "Python", "libchaquopy_java.so"),
    NODEJS("nodejs", "Node.js", "libnode.so"),
    QUICKJS("quickjs", "QuickJS", "libquickjs-wrapper.so"),
    THUNDER("thunder", "Thunder", "libxl_thunder_sdk.so"),
    MPV("mpv", "MPV", "libplayer.so"),
    BDJ("bdj", "BD-J", "");

    public interface Provider {
        PackageInfo installed(String id) throws Exception;
        PackageInfo validate(String id, File apk) throws Exception;
        ClassLoader loader(String id);
        Context context(String id);
        Object create(String id);
    }

    public final String id, title;
    private final String library;
    private static volatile Provider provider;
    private RuntimePlugin entry;
    private File loaded;
    private Object owner;
    private String loadedApk;
    private long loadedVersion;

    RuntimeModule(String id, String title, String library) {
        this.id = id; this.title = title; this.library = library;
    }

    public static void install(Provider value) { provider = java.util.Objects.requireNonNull(value); }
    private static Provider provider() {
        if (provider == null) throw new IllegalStateException("宿主尚未注册模块加载器");
        return provider;
    }
    private PackageInfo info() throws IOException {
        try { return provider().installed(id); }
        catch (Exception error) { throw new IOException(title + " 模块不可用", error); }
    }
    public boolean isInstalled() { try { info(); return true; } catch (IOException error) { return false; } }
    public boolean isExternal() { return true; }
    public String getPackageName() {
        return "com.kukuqi.tvbox.runtime." + id + (Runtime.context().getPackageName().endsWith(".corevalidation") ? ".corevalidation" : "");
    }
    public String getCompatibilityId() { return this == QUICKJS ? "kukuqi-quickjs-ng-0.17-v14" : "tvbox-" + id + (this == PYTHON || this == NODEJS ? "-v2" : "-v1"); }
    public String getVersionName() throws IOException { return info().versionName; }
    @SuppressWarnings("deprecation")
    public long getVersionCode() throws IOException { return info().versionCode; }
    public static void refresh() { /* 每次查询直接校验当前安装包；已加载模块更新后必须重启。 */ }
    public PackageInfo validate(File file) throws IOException {
        try { return provider().validate(id, file); }
        catch (Exception error) { throw new IOException("模块校验失败", error); }
    }
    public File getDirectory() throws IOException { return new File(info().applicationInfo.nativeLibraryDir); }
    public File prepare() throws IOException { return new File(getDirectory(), library); }
    public String getEntryClass() {
        return switch (this) {
            case PYTHON -> "com.kukuqi.tvbox.engine.python.Loader";
            case NODEJS -> "com.kukuqi.nodejs.RuntimeEntry";
            case QUICKJS -> "com.kukuqi.tvbox.engine.quickjs.crawler.Loader";
            case THUNDER -> "com.kukuqi.tvbox.runtime.thunder.ThunderRuntime";
            case BDJ -> "com.kukuqi.tvbox.runtime.bdj.BdjRuntime";
            default -> "";
        };
    }
    private static RuntimeModule moduleForClass(String name) {
        if (name.startsWith("com.kukuqi.tvbox.engine.python.") || name.startsWith("com.chaquo.python.")) return PYTHON;
        if (name.startsWith("com.kukuqi.nodejs.")) return NODEJS;
        if (name.startsWith("com.kukuqi.quickjs.") && !name.startsWith("com.kukuqi.quickjs.host.")) return QUICKJS;
        if (name.startsWith("com.xunlei.downloadlib.")) return THUNDER;
        return null;
    }
    private static final ClassLoader SPIDER_LOADER = new ClassLoader(RuntimeModule.class.getClassLoader()) {
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            RuntimeModule module = moduleForClass(name);
            return module == null ? super.loadClass(name, resolve) : provider().loader(module.id).loadClass(name);
        }
    };
    public static ClassLoader getSpiderClassLoader() { return SPIDER_LOADER; }
    public synchronized <T extends RuntimePlugin> T entry(Class<T> type) {
        require();
        // 仍校验已加载 APK 的身份，禁止更新后继续使用旧的 Java/原生组合。
        provider().loader(id);
        if (entry == null) entry = type.cast(provider().create(id));
        return type.cast(entry);
    }
    public String readAsset(String name) {
        String path = name.replace("assets://", "");
        if (path.startsWith("/") || path.contains("..") || path.contains("\\")) throw new IllegalArgumentException("非法模块资源路径");
        try (var input = provider().context(id).getAssets().open(path)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException("读取模块资源失败：" + path, error); }
    }
    public synchronized void load() { load(System::load); }
    public synchronized void load(Consumer<String> loader) {
        require();
        if (loaded != null) {
            try { if (isRestartRequired()) throw new IllegalStateException("模块已更新，请重启应用"); }
            catch (IOException error) { throw new IllegalStateException(error); }
            return;
        }
        try {
            provider().loader(id);
            File directory = getDirectory();
            String[] libraries = this == NODEJS ? new String[]{"libc++_shared.so", "libnode.so", "libtvbox_node.so"} : new String[]{library};
            for (String name : libraries) loader.accept(new File(directory, name).getAbsolutePath());
            recordLoaded(prepare(), this);
        } catch (IOException error) { throw new IllegalStateException("模块原生库加载失败", error); }
    }
    @SuppressWarnings("deprecation")
    public synchronized boolean isRestartRequired() throws IOException {
        if (loadedApk == null) return false;
        PackageInfo info = info();
        return !loadedApk.equals(info.applicationInfo.sourceDir) || loadedVersion != info.versionCode;
    }
    public synchronized File getLoadedFile() { return loaded; }
    @SuppressWarnings("deprecation")
    public synchronized void recordLoaded(File file, Object owner) throws IOException {
        PackageInfo info = info();
        if (!file.getCanonicalFile().getParentFile().equals(new File(info.applicationInfo.nativeLibraryDir).getCanonicalFile()))
            throw new IOException("模块原生库路径不匹配");
        this.loaded = file; this.owner = owner; loadedApk = info.applicationInfo.sourceDir; loadedVersion = info.versionCode;
    }
    public synchronized void recordStopped(Object owner) {
        if (this.owner != owner) return;
        loaded = null; this.owner = null; loadedApk = null;
    }
    public void clean() throws IOException { info(); /* 使用安装器管理的原生库，无需另一套解压缓存。 */ }
    public void require() { if (!isInstalled()) throw new MissingException(this); }
    public static class MissingException extends IllegalStateException {
        public final RuntimeModule module;
        public MissingException(RuntimeModule module) { super(module.title + " 模块未安装或校验失败，请到模块管理安装"); this.module = module; }
        public static MissingException find(Throwable error) {
            for (Throwable current = error; current != null; current = current.getCause()) if (current instanceof MissingException missing) return missing;
            return null;
        }
    }
}
