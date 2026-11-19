package com.kukuqi.tvbox.osc.player;

import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.view.Surface;
import android.view.SurfaceHolder;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.ui.danmaku.DanmakuConfig;
import com.kukuqi.tvbox.engine.bean.Result;
import com.kukuqi.tvbox.engine.player.PlayerManager;
import com.kukuqi.tvbox.engine.player.media.PlaySpec;
import com.kukuqi.tvbox.engine.setting.PlayerSetting;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import xyz.doikki.videoplayer.player.AbstractPlayer;

/** Adapts the core playback engine to the existing TVboxOSC view and controller lifecycle. */
public final class CorePlayer extends AbstractPlayer implements PlayerManager.Callback {
    private final int engine;
    private final Result result;
    private PlayerManager manager;
    private Surface surface;
    private String path;
    private Map<String,String> headers = new HashMap<>();
    private boolean prepared;
    private Consumer<CueGroup> cues;
    private Consumer<CueGroup> renderedCues;
    private Runnable subtitleStyleChanged;
    private float subtitleTextSize;
    private int subtitleTextColor = android.graphics.Color.WHITE;
    private String pendingSubtitleId, pendingSubtitleName;
    private boolean subtitleVisible = true;
    private java.util.function.BooleanSupplier renderSubtitleEnabled = () -> true;
    private boolean parseInPlayer;
    private boolean softwareDecode;
    private java.io.File descriptorFile;
    private long lastRx = -1, lastRxTime;

    public CorePlayer(int legacyType, Result result) {
        engine = legacyType == 1 ? PlayerSetting.ENGINE_MPV : PlayerSetting.ENGINE_EXO;
        this.result = result;
    }
    @Override public void initPlayer() {
        PlayerSetting.putEngine(engine);
        boolean currentCache = com.orhanobut.hawk.Hawk.get(com.kukuqi.tvbox.osc.util.HawkConfig.IJK_CACHE_PLAY, false);
        boolean nextEpisode = com.orhanobut.hawk.Hawk.get(com.kukuqi.tvbox.osc.util.HawkConfig.NEXT_EPISODE_PRELOAD, true);
        com.kukuqi.tvbox.engine.setting.PreloadSetting.putEnabled(currentCache || nextEpisode);
        com.kukuqi.tvbox.engine.setting.PreloadSetting.putCurrentEnabled(currentCache);
        com.kukuqi.tvbox.engine.setting.PreloadSetting.putNextEpisodeEnabled(nextEpisode);
        manager = new PlayerManager(this);
        manager.setDecode(softwareDecode ? com.kukuqi.tvbox.engine.player.engine.PlayerEngine.SOFT : com.kukuqi.tvbox.engine.player.engine.PlayerEngine.HARD);
        onPlayerRebuild(manager.getPlayer());
        com.kukuqi.tvbox.osc.core.CoreBridge.bindPlayer(this);
    }
    public void setSoftwareDecode(boolean value) { softwareDecode = value; }
    public PlayerManager manager() { return manager; }
    public Player player() { return manager.getPlayer(); }
    public void setCueListener(Consumer<CueGroup> listener) { cues = listener; }
    public void setRenderedCueListener(Consumer<CueGroup> listener) { renderedCues = listener; }
    public void setSubtitleStyleListener(Runnable listener) { subtitleStyleChanged = listener; }
    public float getSubtitleTextSize() { return subtitleTextSize; }
    public int getSubtitleTextColor() { return subtitleTextColor; }
    public void setSubtitleStyle(float pixels, int color) {
        subtitleTextSize = pixels; subtitleTextColor = color;
        if (subtitleStyleChanged != null) subtitleStyleChanged.run();
        if (engine == PlayerSetting.ENGINE_MPV && manager != null) {
            com.kukuqi.tvbox.engine.setting.SubtitleSetting.putStyleMode(com.kukuqi.tvbox.engine.setting.SubtitleSetting.STYLE_CUSTOM);
            com.kukuqi.tvbox.engine.setting.SubtitleSetting.putTextColor(color);
            com.kukuqi.tvbox.engine.setting.SubtitleSetting.putScale(pixels / (22 * com.kukuqi.tvbox.engine.App.get().getResources().getDisplayMetrics().scaledDensity));
            manager.applySubtitleStyle();
        }
    }
    public void setExternalSubtitle(String value) {
        if (value == null || value.isEmpty() || manager == null) return;
        String url = Uri.parse(value).getScheme() == null ? Uri.fromFile(new java.io.File(value)).toString() : value;
        boolean playing = isPlaying(); float speed = getSpeed();
        com.kukuqi.tvbox.engine.bean.Sub sub = com.kukuqi.tvbox.engine.bean.Sub.from(url);
        pendingSubtitleId = "external:" + java.util.UUID.nameUUIDFromBytes(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        pendingSubtitleName = sub.getName();
        manager.setSub(sub);
        setSubtitleEnabled(true);
        player().setPlaybackSpeed(speed);
        if (!playing) player().pause();
    }
    public void setSubtitleEnabled(boolean enabled) {
        subtitleVisible = enabled;
        // Exo follows OSC's visibility toggle: keep decoding cues so a long caption can reappear.
        // MPV paints into the video surface, so its native subtitle track must be disabled.
        if (engine == PlayerSetting.ENGINE_MPV && manager != null && !manager.isReleased()) {
            player().setTrackSelectionParameters(player().getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled).build());
            manager.applySubtitleStyle();
        }
        refreshRenderedSubtitles();
    }
    public void setRenderedSubtitleEnabled(java.util.function.BooleanSupplier value) { renderSubtitleEnabled = value; refreshRenderedSubtitles(); }
    private void refreshRenderedSubtitles() { if (renderedCues != null && manager != null) renderedCues.accept(player().getCurrentCues()); }
    public boolean useRenderedSubtitles() { return engine == PlayerSetting.ENGINE_EXO && subtitleVisible && renderSubtitleEnabled.getAsBoolean(); }
    public void setParseInPlayer(boolean value) { parseInPlayer = value; }
    public TrackInfo getTrackInfo() {
        TrackInfo info = new TrackInfo();
        int groupIndex = 0;
        for (androidx.media3.common.Tracks.Group group : player().getCurrentTracks().getGroups()) {
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                androidx.media3.common.Format format = group.getTrackFormat(i);
                TrackInfoBean item = new TrackInfoBean();
                item.name = format.label == null ? "轨道 " + (i+1) : format.label;
                item.language = format.language;
                item.trackId = i;
                item.trackGroupId = groupIndex;
                item.selected = group.isTrackSelected(i);
                if (group.getType() == C.TRACK_TYPE_AUDIO) info.addAudio(item);
                else if (group.getType() == C.TRACK_TYPE_TEXT) info.addSubtitle(item);
            }
            groupIndex++;
        }
        return info;
    }
    public void selectTrack(TrackInfoBean item) {
        var groups = player().getCurrentTracks().getGroups();
        if (item.trackGroupId >= groups.size()) return;
        var group = groups.get(item.trackGroupId);
        if (group.getType() == C.TRACK_TYPE_TEXT) { pendingSubtitleId = null; pendingSubtitleName = null; }
        player().setTrackSelectionParameters(player().getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(group.getType(), false)
                .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(group.getMediaTrackGroup(),item.trackId)).build());
    }
    @Override public void setDataSource(String path, Map<String,String> headers) {
        this.path = path;
        this.headers = headers == null ? new HashMap<>() : new HashMap<>(headers);
    }
    @Override public void setDataSource(AssetFileDescriptor fd) {
        try {
            descriptorFile = java.io.File.createTempFile("local-media-", ".bin", com.kukuqi.tvbox.engine.App.get().getCacheDir());
            try (java.io.InputStream input = fd.createInputStream(); java.io.OutputStream output = new java.io.FileOutputStream(descriptorFile)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            setDataSource(Uri.fromFile(descriptorFile).toString(), null);
        } catch (java.io.IOException failure) { throw new IllegalArgumentException("无法读取本地媒体", failure); }
    }
    @Override public void prepareAsync() {
        prepared = false;
        if (parseInPlayer && result != null && result.needParse()) {
            manager.parse(result.getKey(),result,result.isUseParse(),MediaMetadata.EMPTY);
            return;
        }
        PlaySpec spec = result == null
                ? PlaySpec.from("", path, headers, MediaMetadata.EMPTY)
                : PlaySpec.from(result, result.getKey(), MediaMetadata.EMPTY);
        spec.setUrl(path);
        spec.setHeaders(headers);
        manager.start(spec, 30000);
    }
    @Override public void start() { player().play(); }
    @Override public void pause() { player().pause(); }
    @Override public void stop() { if (manager != null) manager.stop(); }
    @Override public void reset() { stop(); prepared = false; }
    // DK VideoView uses this as playback intent, including while seeking or buffering.
    @Override public boolean isPlaying() { return manager != null && !manager.isReleased() && player().getPlayWhenReady(); }
    @Override public void seekTo(long position) { player().seekTo(Math.max(0,position)); }
    @Override public void release() {
        com.kukuqi.tvbox.osc.core.CoreBridge.unbindPlayer(this); if (manager != null) { manager.stop(); manager.release(); manager = null; } if (descriptorFile != null) { descriptorFile.delete(); descriptorFile = null; } cues = null; renderedCues = null; subtitleStyleChanged = null; renderSubtitleEnabled = () -> true; surface = null; }
    @Override public long getCurrentPosition() { return manager == null ? 0 : manager.getPosition(); }
    @Override public long getDuration() { return manager == null || player().getDuration() == C.TIME_UNSET ? 0 : player().getDuration(); }
    @Override public int getBufferedPercentage() { return manager == null ? 0 : player().getBufferedPercentage(); }
    @Override public void setSurface(Surface value) { surface = value; if (manager != null) player().setVideoSurface(value); }
    @Override public void setDisplay(SurfaceHolder holder) { setSurface(holder == null ? null : holder.getSurface()); }
    @Override public void setVolume(float left, float right) { player().setVolume((left+right)/2); }
    @Override public void setLooping(boolean looping) { player().setRepeatMode(looping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF); }
    @Override public void setOptions() { }
    @Override public void setSpeed(float speed) { player().setPlaybackSpeed(speed); }
    @Override public float getSpeed() { return manager == null ? 1 : manager.getSpeed(); }
    @Override public long getTcpSpeed() {
        long now = android.os.SystemClock.elapsedRealtime();
        long rx = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid());
        long speed = lastRx < 0 || rx < 0 || now <= lastRxTime ? 0 : Math.max(0, rx - lastRx) * 1000 / (now - lastRxTime);
        lastRx = rx; lastRxTime = now;
        return speed;
    }
    @Override public void onPrepare() { }
    @Override public void onTracksChanged() {
        if (pendingSubtitleId == null || manager == null) return;
        for (androidx.media3.common.Tracks.Group group : player().getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.length; i++) {
                androidx.media3.common.Format format = group.getTrackFormat(i);
                if (!pendingSubtitleId.equals(format.id) && !pendingSubtitleName.equals(format.label)) continue;
                pendingSubtitleId = null; pendingSubtitleName = null;
                player().setTrackSelectionParameters(player().getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(new androidx.media3.common.TrackSelectionOverride(group.getMediaTrackGroup(),i)).build());
                return;
            }
        }
    }
    @Override public void onDecodeChanged() { }
    @Override public void onMediaOptionsChanged() { }
    @Override public void onError(String message) { if (mPlayerEventListener != null) mPlayerEventListener.onError(); }
    @Override public void onPlayerRebuild(Player player) {
        // The existing OSC VideoView owns audio focus for both engines.
        player.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false);
        player.setVideoSurface(surface);
        player.addListener(listener);
    }
    @Override public void onDanmakuSourceChanged(Uri uri) { }
    @Override public void onDanmakuConfigChanged(DanmakuConfig config) { }
    @Override public void onDanmakuEnabledChanged(boolean enabled) { }
    @Override public void onDanmakuSent(String text) { }
    private final Player.Listener listener = new Player.Listener() {
        @Override public void onTracksChanged(androidx.media3.common.Tracks tracks) { CorePlayer.this.onTracksChanged(); }
        @Override public void onPlaybackStateChanged(int state) {
            if (mPlayerEventListener == null) return;
            if (state == Player.STATE_READY) {
                if (!prepared) { prepared = true; mPlayerEventListener.onPrepared(); }
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END,0);
            } else if (state == Player.STATE_BUFFERING && prepared) mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START,0);
            else if (state == Player.STATE_ENDED) mPlayerEventListener.onCompletion();
        }
        @Override public void onRenderedFirstFrame() { if (mPlayerEventListener != null) mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START,0); }
        @Override public void onVideoSizeChanged(VideoSize size) { if (mPlayerEventListener != null) mPlayerEventListener.onVideoSizeChanged(size.width,size.height); }
        @Override public void onCues(CueGroup group) { if (renderedCues != null) renderedCues.accept(group); if (cues != null) cues.accept(group); }
    };
}
