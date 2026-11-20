package com.kukuqi.tvbox.engine.player.subtitle;

/** 复用配套 MPV 模块导出的 libass，首次缺失后允许安装模块再重试。 */
final class AssNative {
    static boolean isAvailable() {
        synchronized (com.github.catvod.utils.RuntimeModule.MPV) {
            try {
                com.github.catvod.utils.RuntimeModule.MPV.load();
                System.loadLibrary("tvbox_ass");
                return available();
            } catch (RuntimeException | LinkageError unavailable) {
                return false;
            }
        }
    }
    static native boolean available();
    static native long open(String config, String fonts, String family);
    static native long read(long context, byte[] script);
    static native long newTrack(long context, byte[] codecPrivate);
    static native void chunk(long track, byte[] data, long startMs, long durationMs, boolean reset);
    static native long endMs(long track);
    static native int[] render(long context, long track, long timeMs);
    static native void freeTrack(long track);
    static native void close(long context);
}
