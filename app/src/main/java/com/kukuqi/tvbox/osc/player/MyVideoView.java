package com.kukuqi.tvbox.osc.player;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoView;

public class MyVideoView extends VideoView {
    private com.kukuqi.tvbox.engine.bean.Result qualities;
    private java.util.function.IntConsumer selectQuality;

    public void setQualityOptions(com.kukuqi.tvbox.engine.bean.Result result, java.util.function.IntConsumer select) {
        qualities = result;
        selectQuality = select;
    }
    public com.kukuqi.tvbox.engine.bean.Result getQualityOptions() { return qualities; }
    public void selectQuality(int position) {
        if (qualities != null && selectQuality != null && position >= 0 && position < qualities.getUrl().getValues().size()) selectQuality.accept(position);
    }
    private boolean sleepPaused;
    private boolean refreshDanmaku, hasDanmakuContext;
    private com.google.gson.JsonElement danmakuSource;
    private String danmakuName = "", danmakuEpisode = "";
    private com.kukuqi.tvbox.osc.danmaku.DanmakuView danmakuView;
    private com.kukuqi.tvbox.osc.danmaku.DanmakuController danmaku;
    private void initDanmaku(Context context) {
        danmakuView = new com.kukuqi.tvbox.osc.danmaku.DanmakuView(context, this);
        danmaku = new com.kukuqi.tvbox.osc.danmaku.DanmakuController(danmakuView);
        mPlayerContainer.addView(danmakuView, new android.widget.FrameLayout.LayoutParams(-1, -1));
    }
    public void setDanmakuContext(com.google.gson.JsonElement source, String name, String episode) {
        danmakuSource = source; danmakuName = name; danmakuEpisode = episode; hasDanmakuContext = true; refreshDanmaku = false;
        danmaku.load(source, name, episode);
    }
    /** 接收 Node 来源推送，沿用当前弹幕渲染和用户开关。 */
    public void setScriptDanmaku(String url) {
        if (url == null || url.isEmpty()) {
            danmakuSource = com.google.gson.JsonNull.INSTANCE;
            refreshDanmaku = false;
            danmaku.clear();
        } else setDanmakuContext(new com.google.gson.JsonPrimitive(url), danmakuName, danmakuEpisode);
    }
    public String getDanmakuStatus() { return danmaku.status(); }
    public int getDanmakuCount() { return danmakuView.count(); }

    @Override public void start() {
        sleepPaused = false; // An explicit press on Play permits playback after the timer.
        SleepTimer.get().register(this);
        if (sleepPaused) return;
        super.start();
        com.kukuqi.tvbox.osc.core.CoreBridge.bindVideoView(this);
        if (refreshDanmaku && hasDanmakuContext) { refreshDanmaku = false; danmaku.load(danmakuSource, danmakuName, danmakuEpisode); }
    }

    @Override public void resume() {
        SleepTimer.get().checkExpired();
        if (!sleepPaused) super.resume();
    }

    public void pauseForSleepTimer() {
        sleepPaused = true;
        pause();
    }

    @Override protected void setPlayState(int state) {
        super.setPlayState(state);
        if (danmakuView != null) danmakuView.invalidate();
        // A player finishing preparation/buffering must not restart after expiration.
        if (sleepPaused && (state == STATE_PLAYING || state == STATE_BUFFERED || state == STATE_PREPARED)) post(this::pause);
    }

    @Override public void release() {
        if (danmaku != null) { danmaku.cancel(); refreshDanmaku = true; }
        SleepTimer.get().unregister(this);
        com.kukuqi.tvbox.osc.core.CoreBridge.unbindVideoView(this);
        super.release();
    }
    public MyVideoView(@NonNull Context context) {
        this(context, null);
    }

    public MyVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MyVideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initDanmaku(context);
    }

    public AbstractPlayer getMediaPlayer() {
        return mMediaPlayer;
    }

}
