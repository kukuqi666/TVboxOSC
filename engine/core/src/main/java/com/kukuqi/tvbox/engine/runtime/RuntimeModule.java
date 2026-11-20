package com.kukuqi.tvbox.engine.runtime;

/** Only modules implemented by this host are advertised. Shared API classes stay in the host. */
public enum RuntimeModule {
    PYTHON("python", "Python", "com.kukuqi.tvbox.engine.python.Loader"),
    QUICKJS("quickjs", "QuickJS", "com.kukuqi.tvbox.engine.quickjs.crawler.Loader"),
    THUNDER("thunder", "Thunder", "com.kukuqi.tvbox.runtime.thunder.ThunderRuntime"),
    NODEJS("nodejs", "Node.js", "com.kukuqi.nodejs.RuntimeEntry"),
    MPV("mpv", "MPV", ""),
    BDJ("bdj", "BD-J", "com.kukuqi.tvbox.runtime.bdj.BdjRuntime");

    public final String id, title, entry;
    RuntimeModule(String id, String title, String entry) {
        this.id = id; this.title = title; this.entry = entry;
    }
    public String packageName(android.content.Context host) {
        return "com.kukuqi.tvbox.runtime." + id +
                (host.getPackageName().endsWith(".corevalidation") ? ".corevalidation" : "");
    }
    public String contract() { if (this == MPV) return "tvbox-mpv-split-ffmpeg-v1"; return "tvbox-" + id + (this == QUICKJS || this == NODEJS || this == PYTHON ? "-v2" : "-v1"); }
}
