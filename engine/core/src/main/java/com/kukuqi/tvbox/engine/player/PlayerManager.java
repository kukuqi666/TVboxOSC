package com.kukuqi.tvbox.engine.player;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaChapter;
import androidx.media3.common.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.danmaku.DanmakuConfig;

import com.kukuqi.tvbox.engine.App;
import com.kukuqi.tvbox.engine.Constant;
import com.kukuqi.tvbox.engine.R;
import com.kukuqi.tvbox.engine.bean.Danmaku;
import com.kukuqi.tvbox.engine.bean.Result;
import com.kukuqi.tvbox.engine.bean.Sub;
import com.kukuqi.tvbox.engine.bean.Track;
import com.kukuqi.tvbox.engine.impl.ParseCallback;
import com.kukuqi.tvbox.engine.player.effect.PlayerEffectManager;
import com.kukuqi.tvbox.engine.player.effect.audio.AudioEffectBands;
import com.kukuqi.tvbox.engine.player.engine.PlayerEngine;
import com.kukuqi.tvbox.engine.player.engine.PlayerEngine.SecondarySubtitleState;
import com.kukuqi.tvbox.engine.player.engine.PlayerEngineFactory;
import com.kukuqi.tvbox.engine.player.media.PlaySpec;
import com.kukuqi.tvbox.engine.player.parse.ParseJob;
import com.kukuqi.tvbox.engine.player.track.TrackUtil;
import com.kukuqi.tvbox.engine.setting.DanmakuSetting;
import com.kukuqi.tvbox.engine.setting.PlayerSetting;
import com.kukuqi.tvbox.engine.setting.PreloadSetting;
import com.kukuqi.tvbox.engine.setting.SpeedSetting;
import com.kukuqi.tvbox.engine.utils.Notify;
import com.kukuqi.tvbox.engine.utils.ResUtil;
import com.kukuqi.tvbox.engine.utils.Util;
import com.google.common.net.HttpHeaders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerManager implements ParseCallback {

    private final PlayerEffectManager effects;
    private final Runnable runnable;
    private final Callback callback;
    private PendingPreload pendingPreload;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private PlayerView playerView;
    private float videoScaleX = 1f;
    private float videoScaleY = 1f;
    private float videoAspect = Float.NaN;
    private Player player;

    private long pendingStartPositionMs;
    private boolean danmakuEnabled;
    private boolean initTrack;
    private int retry;
    private int decode;

    public PlayerManager(Callback callback) {
        this.callback = callback;
        this.decode = PlayerEngine.HARD;
        this.runnable = this::onPlayTimeout;
        this.pendingStartPositionMs = C.TIME_UNSET;
        this.engine = PlayerEngineFactory.create(decode, listener);
        this.effects = new PlayerEffectManager(() -> engine);
        this.danmakuEnabled = DanmakuSetting.isShow();
        this.player = engine.getPlayer();
    }

    public void release() {
        stopParse();
        App.removeCallbacks(runnable);
        if (player != null) player.removeListener(listener);
        if (engine != null) engine.release();
        engine = null;
        player = null;
    }

    public Player getPlayer() {
        return player;
    }

    private void setPlayer(Player player) {
        this.player = player;
        callback.onPlayerRebuild(player);
    }

    public Tracks getCurrentTracks() {
        return player.getCurrentTracks();
    }

    public int getAudioChannelCount() {
        return engine == null ? Format.NO_VALUE : engine.getAudioChannelCount();
    }

    public List<MediaChapter> getCurrentMediaChapters() {
        return player.getCurrentMediaChapters();
    }

    public List<MediaEdition> getCurrentMediaEditions() {
        return player.getCurrentMediaEditions();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : List.of();
    }

    private void notifyDanmakuSourceChanged() {
        callback.onDanmakuSourceChanged(getSelectedDanmakuUri());
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public String getMediaTitle() {
        MediaMetadata metadata = getMetadata();
        if (metadata == null) return "";
        CharSequence title = !TextUtils.isEmpty(metadata.displayTitle) ? metadata.displayTitle : metadata.title;
        if (TextUtils.isEmpty(title)) title = metadata.artist;
        return TextUtils.isEmpty(title) ? "" : title.toString();
    }

    public void setMetadata(@NonNull MediaMetadata metadata) {
        if (spec == null || metadata.equals(spec.getMetadata())) return;
        spec.setMetadata(metadata);
        if (TextUtils.isEmpty(spec.getUrl())) return;
        MediaItem current = player.getCurrentMediaItem();
        if (current != null) player.replaceMediaItem(player.getCurrentMediaItemIndex(), current.buildUpon().setMediaMetadata(metadata).build());
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return player.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean hasPlaySpec() {
        return spec != null;
    }

    public boolean canPreloadNext() {
        return PreloadSetting.isEnabled() && PreloadSetting.isNextEpisodeEnabled() && engine != null && engine.getType() == PlayerEngine.Type.EXO;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return player.getCurrentMediaItem() != null && player.isCurrentMediaItemLive();
    }

    public boolean isVod() {
        return player.getCurrentMediaItem() != null && !player.isCurrentMediaItemLive();
    }

    public boolean haveTrack(int type) {
        return TrackUtil.count(getCurrentTracks(), type) > 0;
    }

    public boolean haveEdition() {
        return !getCurrentMediaEditions().isEmpty();
    }

    public boolean haveChapter() {
        return !getCurrentMediaChapters().isEmpty();
    }

    public boolean haveDanmaku() {
        return spec != null && spec.getSelectedDanmaku() != null;
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public String getSizeText() {
        return (getVideoWidth() == 0 && getVideoHeight() == 0) ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.core_select_decode)[decode];
    }

    public boolean canSetAudioSetting() {
        return effects.canSetAudioSetting();
    }

    public AudioEffectBands getAudioSettingBands() {
        return effects.getAudioSettingBands();
    }

    public int getAudioSettingError() {
        return effects.getAudioSettingError();
    }

    public boolean canSetVideoSetting() {
        return effects.canSetVideoSetting();
    }

    public int getVideoSettingError() {
        return effects.getVideoSettingError();
    }

    public boolean supportsVideoSharpness() {
        return effects.supportsVideoSharpness();
    }

    public int getEngine() {
        return isMpvEngine() ? PlayerSetting.ENGINE_MPV : PlayerSetting.ENGINE_EXO;
    }

    public void setEngine(int targetEngine) {
        PlayerSetting.putEngine(targetEngine);
        if (isEmpty() || PlayerEngineFactory.matches(engine, spec)) return;
        PlaybackSnapshot snapshot = PlaybackSnapshot.capture(player);
        startCurrent(snapshot.positionMs());
        snapshot.restore(player);
    }

    public String getPositionTime(long delta) {
        return Util.timeMs(Math.clamp(getPosition() + delta, 0, Math.max(0, getDuration())));
    }

    public long getDuration() {
        return player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public List<Sub> getSubs() {
        return spec == null ? List.of() : spec.getSubs();
    }

    public void setSub(Sub sub) {
        if (sub == null || sub.isEmpty()) return;
        if (spec != null) spec.setSub(sub);
        if (engine.addSubtitle(sub)) play();
        else startCurrent();
    }

    public void selectChapter(MediaChapter chapter) {
        player.selectChapter(chapter);
    }

    public void selectEdition(MediaEdition edition) {
        player.selectEdition(edition);
    }

    public void setDanmakuConfig(DanmakuConfig config) {
        callback.onDanmakuConfigChanged(config);
    }

    public void setDanmakuEnabled(boolean enabled) {
        if (danmakuEnabled == enabled) return;
        danmakuEnabled = enabled;
        callback.onDanmakuEnabledChanged(danmakuEnabled);
    }

    public void applySubtitleStyle() {
        if (engine != null) engine.applySubtitleStyle();
    }

    public void setSecondarySubtitleOutput(java.util.function.Consumer<androidx.media3.common.text.CueGroup> output) {
        if (engine != null) engine.setSecondarySubtitleOutput(output);
    }

    public SecondarySubtitleState getSecondarySubtitleState() {
        return engine == null ? SecondarySubtitleState.EMPTY : engine.getSecondarySubtitleState();
    }

    public void setSecondarySubtitleSelection(@Nullable TrackSelectionOverride selection) {
        if (engine != null) engine.setSecondarySubtitleSelection(selection);
    }

    public void sendDanmaku(String text) {
        callback.onDanmakuSent(text);
    }

    public float setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeed();
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(SpeedSetting.clamp(speed)));
        return getSpeed();
    }

    public float toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? SpeedSetting.getLongPress() : 1);
    }

    public boolean supportsSkipSilence() {
        return effects.supportsSkipSilence();
    }

    public boolean isSkipSilence() {
        return effects.isSkipSilence();
    }

    public void setSkipSilenceEnabled(boolean enabled) {
        effects.setSkipSilenceEnabled(enabled);
    }

    public void setTrack(Track track) {
        TrackUtil.setTrackSelection(player, track);
    }

    public void setVideoSetting(int preset) {
        effects.setVideoSetting(preset);
    }

    public void refreshVideoSetting() {
        effects.refreshVideoSetting();
    }

    public void previewVideoSetting(boolean original) {
        effects.previewVideoSetting(original);
    }

    public void setAudioSetting(int preset) {
        effects.setAudioSetting(preset);
    }

    public void refreshAudioSetting() {
        effects.refreshAudioSetting();
    }

    public void previewAudioSetting(boolean original) {
        effects.previewAudioSetting(original);
    }

    private boolean isMpvEngine() {
        return engine != null && engine.getType() == PlayerEngine.Type.MPV;
    }

    public void play() {
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        engine.stop();
        stopParse();
    }

    public void clearMediaItems() {
        player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return player.getRepeatMode() == Player.REPEAT_MODE_ONE;
    }

    public void setRepeatOne(boolean repeat) {
        player.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public void replay(long positionMs) {
        if (positionMs == C.TIME_UNSET) player.seekToDefaultPosition();
        else player.seekTo(positionMs);
        player.play();
    }

    public void seekTo(long time) {
        player.seekTo(time);
    }

    public long getTextOffsetMs() {
        return player.isCommandAvailable(Player.COMMAND_GET_TEXT_OFFSET) ? player.getTextOffsetMs() : 0;
    }

    public void setTextOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_TEXT_OFFSET)) player.setTextOffsetMs(offsetMs);
    }

    public long getAudioOffsetMs() {
        return player.isCommandAvailable(Player.COMMAND_GET_AUDIO_OFFSET) ? player.getAudioOffsetMs() : 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_AUDIO_OFFSET)) player.setAudioOffsetMs(offsetMs);
    }

    public void reset() {
        App.removeCallbacks(runnable);
        retry = 0;
    }

    public void clear() {
        spec = null;
    }

    public boolean preload(PlaySpec spec, long startPositionMs) {
        if (!canPreloadNext() || spec == null || !PlayerEngineFactory.matches(engine, spec)) return false;
        pendingPreload = new PendingPreload(spec.checkUa(), Math.max(0, startPositionMs));
        startPreloadIfReady();
        return true;
    }

    public void clearPreload() {
        pendingPreload = null;
        if (engine != null) engine.clearPreload();
    }

    public void bindPlayerView(PlayerView playerView) {
        this.playerView = playerView;
        if (engine != null) engine.bindPlayerView(playerView);
        applyVideoTransform();
    }

    public float getVideoScaleX() {
        return videoScaleX;
    }

    public float getVideoScaleY() {
        return videoScaleY;
    }

    /** {@code NaN} leaves the axis untouched, so {@code set video-scale-x 1.2} keeps the Y scale. */
    public void setVideoScale(float scaleX, float scaleY) {
        if (!Float.isNaN(scaleX)) videoScaleX = Math.max(0.1f, Math.min(4f, scaleX));
        if (!Float.isNaN(scaleY)) videoScaleY = Math.max(0.1f, Math.min(4f, scaleY));
        applyVideoTransform();
    }

    /** {@code NaN} restores the media's own aspect ratio. */
    public void setVideoAspect(float ratio) {
        videoAspect = Float.isNaN(ratio) || ratio <= 0 ? Float.NaN : ratio;
        applyVideoTransform();
    }

    /** Aspect override is kept as state only: media3 PlayerView has no ratio setter of its own. */
    public float getVideoAspect() {
        return videoAspect;
    }

    private void applyVideoTransform() {
        if (playerView == null) return;
        playerView.setScaleX(videoScaleX);
        playerView.setScaleY(videoScaleY);
    }

    public float getVolume() {
        return player.isCommandAvailable(Player.COMMAND_GET_VOLUME) ? player.getVolume() : 1f;
    }

    public void setVolume(float volume) {
        if (player.isCommandAvailable(Player.COMMAND_SET_VOLUME)) player.setVolume(Math.max(0f, Math.min(1f, volume)));
    }

    /** Seeks to the Nth chapter; indexes outside the list are ignored. */
    public void seekToChapter(int index) {
        List<MediaChapter> chapters = getCurrentMediaChapters();
        if (index < 0 || index >= chapters.size()) return;
        selectChapter(chapters.get(index));
    }

    public void resetTrack() {
        TrackUtil.reset(player);
    }

    public void toggleDecode() {
        setDecode(isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD);
    }

    private void handleDecodeError(PlaybackException e) {
        if (++retry > 1) callback.onError(engine.getErrorMessage(e));
        else retryDecode(isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD);
    }

    private void retryDecode(int decode) {
        setDecode(decode);
        PlaybackSnapshot snapshot = PlaybackSnapshot.capture(player);
        startCurrent(snapshot.positionMs());
        snapshot.restore(player);
    }

    public void setDecode(int decode) {
        this.decode = decode;
        engine.setDecode(decode);
        callback.onDecodeChanged();
    }

    private boolean isHard() {
        return decode == PlayerEngine.HARD;
    }

    private void onPlayTimeout() {
        callback.onError(ResUtil.getString(R.string.core_error_play_timeout));
        stop();
    }

    private void ensureEngine(PlaySpec spec) {
        if (PlayerEngineFactory.matches(engine, spec)) return;
        PlayerEngine old = engine;
        player.removeListener(listener);
        engine = PlayerEngineFactory.create(decode, spec, listener);
        setPlayer(engine.getPlayer());
        old.release();
    }

    public void browse(PlaySpec spec, long startPositionMs) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY, startPositionMs);
    }

    public void start(PlaySpec spec, long timeout) {
        start(spec, timeout, C.TIME_UNSET);
    }

    public void start(PlaySpec spec, long timeout, long startPositionMs) {
        this.spec = spec;
        setMediaItem(timeout, startPositionMs);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        parse(key, result, useParse, metadata, C.TIME_UNSET);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata, long startPositionMs) {
        stopParse();
        pendingStartPositionMs = startPositionMs;
        spec = PlaySpec.fromParse(result, key, metadata);
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
        pendingStartPositionMs = C.TIME_UNSET;
    }

    private void setMediaItem(long timeout, long startPositionMs) {
        if (spec == null || spec.getUrl() == null) return;
        ensureEngine(spec.checkUa());
        pendingPreload = null;
        initTrack = false;
        engine.start(spec, startPositionMs);
        notifyDanmakuSourceChanged();
        App.post(runnable, timeout);
        callback.onPrepare();
    }

    private void startCurrent() {
        startCurrent(getPosition());
    }

    private void startCurrent(long startPositionMs) {
        setMediaItem(Constant.TIMEOUT_PLAY, startPositionMs);
    }

    private void startPreloadIfReady() {
        PendingPreload preload = pendingPreload;
        if (preload == null || player.getPlaybackState() != Player.STATE_READY) return;
        pendingPreload = null;
        engine.preload(preload.spec(), preload.startPositionMs());
    }

    @Nullable
    public Uri getSelectedDanmakuUri() {
        Danmaku item = spec != null ? spec.getSelectedDanmaku() : null;
        return item == null ? null : item.getUri();
    }

    public void setDanmaku(Danmaku item) {
        if (spec == null) return;
        spec.selectDanmaku(item);
        notifyDanmakuSourceChanged();
    }

    public void toggleDanmaku(Danmaku item) {
        if (spec == null) return;
        spec.toggleDanmaku(item);
        notifyDanmakuSourceChanged();
    }

    public void addDanmaku(Danmaku item) {
        if (spec != null) spec.addDanmaku(item);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.core_parse_from, from));
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        startCurrent(pendingStartPositionMs);
        pendingStartPositionMs = C.TIME_UNSET;
    }

    @Override
    public void onParseError() {
        pendingStartPositionMs = C.TIME_UNSET;
        callback.onError(ResUtil.getString(R.string.core_error_play_parse));
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onDecodeChanged();

        void onMediaOptionsChanged();

        void onError(String msg);

        void onPlayerRebuild(Player newPlayer);

        void onDanmakuSourceChanged(@Nullable Uri uri);

        void onDanmakuConfigChanged(DanmakuConfig config);

        void onDanmakuEnabledChanged(boolean enabled);

        void onDanmakuSent(String text);
    }

    private record PendingPreload(PlaySpec spec, long startPositionMs) {
    }

    private record PlaybackSnapshot(long positionMs, boolean playWhenReady, PlaybackParameters playbackParameters, int repeatMode, float volume, long audioOffsetMs, long textOffsetMs) {

        private static PlaybackSnapshot capture(Player player) {
            float volume = player.isCommandAvailable(Player.COMMAND_GET_VOLUME) ? player.getVolume() : Float.NaN;
            long audioOffsetMs = player.isCommandAvailable(Player.COMMAND_GET_AUDIO_OFFSET) ? player.getAudioOffsetMs() : C.TIME_UNSET;
            long textOffsetMs = player.isCommandAvailable(Player.COMMAND_GET_TEXT_OFFSET) ? player.getTextOffsetMs() : C.TIME_UNSET;
            return new PlaybackSnapshot(player.getCurrentPosition(), player.getPlayWhenReady(), player.getPlaybackParameters(), player.getRepeatMode(), volume, audioOffsetMs, textOffsetMs);
        }

        private void restore(Player player) {
            if (player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) player.setPlaybackParameters(playbackParameters);
            if (player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) player.setRepeatMode(repeatMode);
            if (!Float.isNaN(volume) && player.isCommandAvailable(Player.COMMAND_SET_VOLUME)) player.setVolume(volume);
            if (audioOffsetMs != C.TIME_UNSET && player.isCommandAvailable(Player.COMMAND_SET_AUDIO_OFFSET)) player.setAudioOffsetMs(audioOffsetMs);
            if (textOffsetMs != C.TIME_UNSET && player.isCommandAvailable(Player.COMMAND_SET_TEXT_OFFSET)) player.setTextOffsetMs(textOffsetMs);
            if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) player.setPlayWhenReady(playWhenReady);
        }
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) App.removeCallbacks(runnable);
            if (state == Player.STATE_READY) startPreloadIfReady();
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (tracks.isEmpty() || initTrack) return;
            initTrack = true;
            TrackUtil.setTrackSelection(player, Track.find(getKey()));
            callback.onTracksChanged();
        }

        @Override
        public void onMediaChaptersChanged(@NonNull List<MediaChapter> chapters) {
            callback.onMediaOptionsChanged();
        }

        @Override
        public void onMediaEditionsChanged(@NonNull List<MediaEdition> editions) {
            callback.onMediaOptionsChanged();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            if (spec == null) return;
            PlayerEngine.ErrorAction action = engine.handleError(e);
            if (action != PlayerEngine.ErrorAction.RECOVERED) App.removeCallbacks(runnable);
            switch (action) {
                case DECODE -> handleDecodeError(e);
                case RECOVERED -> notifyDanmakuSourceChanged();
                case FATAL -> callback.onError(engine.getErrorMessage(e));
            }
        }
    };
}
