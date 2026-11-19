package com.kukuqi.tvbox.engine.runtime;

import com.github.catvod.runtime.NodeRuntimeApi;
import com.github.catvod.utils.RuntimeModule;
import com.kukuqi.tvbox.engine.App;

/** Node bundle 只在独立进程执行，宿主保持现有界面和配置模型。 */
public final class NodeRuntime {
    private static final NodeRuntime INSTANCE = new NodeRuntime();
    private NodeRuntimeApi.Client client;
    private volatile NodeRuntimeApi.Callback callback;
    private NodeRuntime() {}
    public static NodeRuntime get() { return INSTANCE; }
    public static boolean isConfig(String url) {
        if (url == null || url.regionMatches(true, 0, "content://", 0, 10)) return false;
        String path = url.split("[?#]", 2)[0];
        try { path = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ignored) {}
        return path.toLowerCase(java.util.Locale.ROOT).endsWith("/index.js.md5") || path.equalsIgnoreCase("index.js.md5");
    }
    public void setCallback(NodeRuntimeApi.Callback callback) { this.callback = callback; }
    public synchronized NodeRuntimeApi.Client client() {
        if (client == null) client = RuntimeModule.NODEJS.entry(NodeRuntimeApi.class).create(App.get(), new NodeRuntimeApi.Callback() {
            @Override public String onMessage(String action, com.google.gson.JsonObject options) {
                NodeRuntimeApi.Callback current = callback;
                return current == null ? "{\"success\":false,\"code\":-1}" : current.onMessage(action, options);
            }
            @Override public void onPlayInfo(String flag, String id) {
                NodeRuntimeApi.Callback current = callback;
                if (current != null) current.onPlayInfo(flag, id);
            }
        });
        return client;
    }
    public String loadConfig(String source) throws Exception {
        NodeRuntimeApi.Client current = client();
        NodeRuntimeApi.LoadedConfig loaded = null;
        try {
            loaded = current.loadConfig(source);
            current.accept(loaded);
            return loaded.json();
        } catch (Exception | Error failure) {
            try { current.fail(loaded); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    public synchronized void clear() { if (client != null) client.clear(); }
}
