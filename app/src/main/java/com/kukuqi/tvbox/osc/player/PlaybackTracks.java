package com.kukuqi.tvbox.osc.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.Toast;
import java.util.List;
import xyz.doikki.videoplayer.player.AbstractPlayer;

public final class PlaybackTracks {
    private PlaybackTracks() {}
    private static TrackInfo tracks(AbstractPlayer player) {
        if (player instanceof CorePlayer) return ((CorePlayer) player).getTrackInfo();
        return null;
    }
    public static void select(Activity activity, MyVideoView view, com.kukuqi.tvbox.osc.subtitle.widget.SimpleSubtitleView subtitles, boolean audio) {
        AbstractPlayer player = view.getMediaPlayer(); TrackInfo info = tracks(player);
        List<TrackInfoBean> items = info == null ? java.util.Collections.emptyList() : audio ? info.getAudio() : info.getSubtitle();
        if (items.isEmpty()) { Toast.makeText(activity, audio ? "当前播放器没有可切换的音轨" : "没有内置字幕", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[items.size()]; int selected = -1;
        for (int i = 0; i < items.size(); i++) {
            TrackInfoBean item = items.get(i); names[i] = item.name + (item.language == null ? "" : " " + item.language);
            if (item.selected) selected = i;
        }
        new AlertDialog.Builder(activity).setTitle(audio ? "切换音轨" : "切换内置字幕")
                .setSingleChoiceItems(names, selected, (dialog, index) -> {
                    TrackInfoBean item = items.get(index);
                    boolean playing = view.isPlaying(); long position = player.getCurrentPosition();
                    player.pause();
                    if (!audio) { subtitles.destroy(); subtitles.clearSubtitleCache(); subtitles.isInternal = true; subtitles.setVisibility(View.VISIBLE); }
                    if (player instanceof CorePlayer) ((CorePlayer) player).selectTrack(item);
                    view.postDelayed(() -> {
                        if (view.getMediaPlayer() != player || activity.isDestroyed()) return;
                        player.seekTo(position); if (playing) player.start();
                    }, 800);
                    dialog.dismiss();
                }).setNegativeButton("取消", null).show();
    }
    public static void bind(MyVideoView view, com.kukuqi.tvbox.osc.subtitle.widget.SimpleSubtitleView subtitles) {
        AbstractPlayer player = view.getMediaPlayer(); TrackInfo info = tracks(player);
        subtitles.hasInternal = info != null && !info.getSubtitle().isEmpty();
        subtitles.bindToMediaPlayer(player);
        if (player instanceof CorePlayer) {
            subtitles.isInternal = subtitles.hasInternal;
            ((CorePlayer) player).setRenderedSubtitleEnabled(() -> subtitles.isInternal && subtitles.getVisibility() == View.VISIBLE);
            ((CorePlayer) player).setSubtitleStyle(subtitles.getTextSize(), subtitles.getCurrentTextColor());
            // Core cues use Media3's renderer, including positioned and bitmap subtitles.
            subtitles.onSubtitleChanged(null);
        }

    }
}
