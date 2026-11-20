package com.kukuqi.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaChapter;
import androidx.media3.common.MediaEdition;
import com.kukuqi.tvbox.engine.bean.Result;
import com.kukuqi.tvbox.engine.player.PlayerManager;
import com.kukuqi.tvbox.engine.player.effect.audio.AudioEffectBands;
import com.kukuqi.tvbox.engine.player.effect.audio.AudioEffectPreset;
import com.kukuqi.tvbox.engine.player.effect.video.VideoEffectPreset;
import com.kukuqi.tvbox.engine.player.effect.video.VideoEffectProfile;
import com.kukuqi.tvbox.engine.player.mpv.MpvConfigFile;
import com.kukuqi.tvbox.engine.setting.AudioSetting;
import com.kukuqi.tvbox.engine.setting.PlayerSetting;
import com.kukuqi.tvbox.engine.setting.SubtitleSetting;
import com.kukuqi.tvbox.engine.setting.VideoSetting;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.player.CorePlayer;
import com.kukuqi.tvbox.osc.player.MyVideoView;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.Utils;
import com.orhanobut.hawk.Hawk;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Functional settings panels shared by My and the standalone settings host. */
public final class FeatureSettingsDialog {
    private final Activity activity;
    private final LinearLayout content;
    private final MaxHeightLinearLayout root;
    private final Dialog dialog;
    private FeatureSettingsDialog(Activity activity, String title) {
        this.activity = activity;
        root = new MaxHeightLinearLayout(activity);
        root.setMaxHeight(Math.min((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.82f), dp(680)));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(14));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(activity.getColor(R.color.text_foreground));
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setPadding(dp(4), dp(12), dp(4), dp(12));
        header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = new TextView(activity);
        close.setText("×");
        close.setTextSize(28);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(activity.getColor(R.color.text_foreground));
        close.setContentDescription("关闭");
        close.setFocusable(true);
        header.addView(close, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(header);

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(2), 0, dp(2), dp(4));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog = new Dialog(activity, R.style.CustomDialogStyle);
        dialog.setContentView(root);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(ignored -> styleWindow(dialog));
    }
    private int dp(int value) { return (int) (value * activity.getResources().getDisplayMetrics().density); }
    private TextView row(String label, Runnable click) {
        TextView text = new TextView(activity); text.setText(label); text.setTextSize(16); text.setPadding(dp(12), dp(15), dp(12), dp(15));
        text.setTextColor(activity.getColor(click == null ? R.color.text_sub_foreground : R.color.text_foreground));
        text.setAlpha(click == null ? 0.82f : 1f);
        text.setMinHeight(dp(52));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setFocusable(click != null);
        if (click != null) {
            TypedValue value = new TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            text.setBackgroundResource(value.resourceId);
            text.setOnClickListener(v -> click.run());
        }
        content.addView(text); return text;
    }

    private void styleWindow(Dialog target) {
        Window window = target.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = Math.min(dp(400), (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.88f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.dimAmount = 0.28f;
        window.setAttributes(params);
        window.setWindowAnimations(R.style.DialogFadeAnimation);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        root.setBackgroundResource(R.drawable.bg_dialog);
        root.setElevation(dp(12));
    }
    private static final class MaxHeightLinearLayout extends LinearLayout {
        private int maxHeight = Integer.MAX_VALUE;
        MaxHeightLinearLayout(Activity activity) { super(activity); }
        void setMaxHeight(int value) { maxHeight = value; }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int capped = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, capped);
        }
    }
    public static void select(Activity activity, String title, String[] names, int selected, IntConsumer onSelect) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, title);
        int checked = Math.max(-1, Math.min(names.length - 1, selected));
        for (int i = 0; i < names.length; i++) {
            int index = i;
            TextView option = panel.row(names[i], () -> {
                onSelect.accept(index);
                panel.dialog.dismiss();
            });
            option.setSelected(i == checked);
            if (i == checked) {
                option.setTextColor(activity.getColor(R.color.colorPrimary));
                option.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                option.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.checkbox_on_background, 0);
            }
        }
        panel.dialog.show();
    }
    public static void actions(Activity activity, String title, String[] names, IntConsumer onSelect) {
        select(activity, title, names, -1, onSelect);
    }

    public static void corePlayback(Activity activity, MyVideoView view) {
        if (!(view.getMediaPlayer() instanceof CorePlayer)) {
            Toast.makeText(activity, "请先开始播放", Toast.LENGTH_SHORT).show();
            return;
        }
        CorePlayer core = (CorePlayer) view.getMediaPlayer();
        PlayerManager manager = core.manager();
        if (manager == null || manager.isReleased()) return;
        BooleanSupplier active = () -> !activity.isDestroyed() && view.getMediaPlayer() == core && !manager.isReleased();
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, "更多播放设置");
        Result qualities = view.getQualityOptions();
        if (qualities != null && qualities.getUrl().isMulti()) {
            panel.row("清晰度 · " + qualityName(qualities, qualities.getUrl().getPosition()) + " ›", () -> {
                panel.dialog.dismiss();
                String[] names = new String[qualities.getUrl().getValues().size()];
                for (int i = 0; i < names.length; i++) names[i] = qualityName(qualities, i);
                select(activity, "切换清晰度", names, qualities.getUrl().getPosition(), index -> {
                    if (view.getQualityOptions() == qualities) view.selectQuality(index);
                });
            });
        }
        List<MediaChapter> chapters = manager.getCurrentMediaChapters();
        if (!chapters.isEmpty()) panel.row("章节 · " + chapters.size() + " ›", () -> {
            panel.dialog.dismiss();
            String[] names = new String[chapters.size()]; int selected = -1;
            for (int i = 0; i < names.length; i++) {
                MediaChapter item = chapters.get(i);
                names[i] = item.label + (item.timeUs == C.TIME_UNSET ? "" : " · " + playbackTime(item.timeUs / 1000));
                if (item.selected) selected = i;
            }
            select(activity, "选择章节", names, selected, index -> { if (active.getAsBoolean()) manager.selectChapter(chapters.get(index)); });
        });
        List<MediaEdition> editions = manager.getCurrentMediaEditions();
        if (!editions.isEmpty()) panel.row("影片版本 · " + editions.size() + " ›", () -> {
            panel.dialog.dismiss();
            String[] names = new String[editions.size()]; int selected = -1;
            for (int i = 0; i < names.length; i++) {
                MediaEdition item = editions.get(i);
                names[i] = item.label + (item.durationUs == C.TIME_UNSET ? "" : " · " + playbackTime(item.durationUs / 1000));
                if (item.selected) selected = i;
            }
            select(activity, "选择影片版本", names, selected, index -> { if (active.getAsBoolean()) manager.selectEdition(editions.get(index)); });
        });
        panel.row("画面调节 ›", () -> {
            panel.dialog.dismiss();
            if (active.getAsBoolean()) videoEffects(activity, manager, active);
        });
        panel.row("声音调节 ›", () -> {
            panel.dialog.dismiss();
            if (active.getAsBoolean()) audioEffects(activity, manager, active);
        });
        panel.row("音画／字幕同步 ›", () -> {
            panel.dialog.dismiss();
            if (active.getAsBoolean()) playbackOffsets(activity, manager, active);
        });
        panel.row("双字幕 ›", () -> {
                panel.dialog.dismiss();
                if (active.getAsBoolean()) secondarySubtitle(activity, manager, active);
            });
        if (manager.getEngine() == PlayerSetting.ENGINE_MPV) {
            panel.row("MPV 自定义配置 ›", () -> {
                panel.dialog.dismiss();
                edit(activity, "MPV 自定义配置", "每行填写一个 mpv.conf 选项，保存后下次播放生效。播放器管理的选项仍以界面设置为准。",
                        MpvConfigFile.read(), "例如：deband=yes", value -> {
                            boolean saved = MpvConfigFile.write(value);
                            Toast.makeText(activity, saved ? "已保存，下次播放生效" : "保存失败", Toast.LENGTH_SHORT).show();
                        });
            });
        }
        panel.dialog.show();
    }

    private static String qualityName(Result result, int index) {
        String name = result.getUrl().n(index);
        return name == null || name.trim().isEmpty() ? "清晰度 " + (index + 1) : name;
    }
    private static String playbackTime(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private void range(String label, double min, double max, double step, double value, String unit,
                       BooleanSupplier active, DoubleConsumer setter) {
        TextView title = row(label, null);
        SeekBar slider = new SeekBar(activity);
        slider.setMax((int) Math.round((max - min) / step));
        slider.setProgress((int) Math.round((Math.max(min, Math.min(max, value)) - min) / step));
        slider.setContentDescription(label);
        IntConsumer render = progress -> title.setText(label + "：" +
                String.format(Locale.ROOT, step >= 1 ? "%.0f" : "%.2f", min + progress * step) + unit);
        render.accept(slider.getProgress());
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                render.accept(progress);
                if (fromUser && active.getAsBoolean()) setter.accept(min + progress * step);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    }

    private static void videoEffects(Activity activity, PlayerManager manager, BooleanSupplier active) {
        if (!manager.canSetVideoSetting()) {
            message(activity, "画面调节", "当前视频或渲染方式不支持画面调节，可切换播放器或渲染方式后重试。");
            return;
        }
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, "画面调节");
        String[] presets = {"关闭", "自然", "鲜艳", "清晰", "明亮", "影院", "柔和", "暖色", "冷色", "护眼", "动漫", "运动", "游戏", "自定义"};
        panel.row("画面预设 ›", () -> {
            panel.dialog.dismiss();
            select(activity, "画面预设", presets, VideoSetting.isEnabled() ? VideoSetting.getPreset() : 0,
                    index -> { if (active.getAsBoolean()) { manager.setVideoSetting(index); videoEffects(activity, manager, active); } });
        });
        VideoEffectProfile profile = VideoSetting.getAppliedProfile();
        boolean[] initialized = {false};
        BooleanSupplier editable = () -> {
            if (!active.getAsBoolean()) return false;
            if (!initialized[0]) {
                VideoSetting.putCustomProfile(profile);
                initialized[0] = true;
            }
            return true;
        };
        DoubleConsumer refresh = ignored -> manager.setVideoSetting(VideoEffectPreset.CUSTOM);
        panel.range("饱和度", .5, 2, .05, profile.getSaturation(), "", editable, value -> { VideoSetting.putSaturation((float)value); refresh.accept(value); });
        panel.range("对比度", .5, 1.8, .05, profile.getContrast(), "", editable, value -> { VideoSetting.putContrast((float)value); refresh.accept(value); });
        panel.range("亮度", -.2, .2, .01, profile.getBrightness(), "", editable, value -> { VideoSetting.putBrightness((float)value); refresh.accept(value); });
        if (manager.supportsVideoSharpness()) panel.range("锐度", 0, .8, .02, profile.getSharpness(), "", editable, value -> { VideoSetting.putSharpness((float)value); refresh.accept(value); });
        panel.range("暗部提亮", 0, .6, .02, profile.getShadowLift(), "", editable, value -> { VideoSetting.putShadow((float)value); refresh.accept(value); });
        panel.range("伽马", .5, 2, .05, profile.getGamma(), "", editable, value -> { VideoSetting.putGamma((float)value); refresh.accept(value); });
        panel.range("色相", -180, 180, 1, profile.getHue(), "°", editable, value -> { VideoSetting.putHue((float)value); refresh.accept(value); });
        panel.range("色温", -100, 100, 1, profile.getTemperature(), "", editable, value -> { VideoSetting.putTemperature((float)value); refresh.accept(value); });
        panel.row("恢复原始画面", () -> { if (active.getAsBoolean()) { VideoSetting.reset(); manager.refreshVideoSetting(); } panel.dialog.dismiss(); });
        panel.dialog.show();
    }

    private static void audioEffects(Activity activity, PlayerManager manager, BooleanSupplier active) {
        if (!manager.canSetAudioSetting()) {
            message(activity, "声音调节", "当前音频输出不支持音效，可关闭音频直通或切换播放器后重试。");
            return;
        }
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, "声音调节");
        String[] presets = {"关闭", "自然", "人声", "影院", "低音", "高音", "流行", "摇滚", "舞曲", "电子", "嘻哈", "爵士", "古典", "自定义"};
        panel.row("音效预设 ›", () -> {
            panel.dialog.dismiss();
            select(activity, "音效预设", presets, AudioSetting.isEnabled() ? AudioSetting.getPreset() : 0,
                    index -> { if (active.getAsBoolean()) { manager.setAudioSetting(index); audioEffects(activity, manager, active); } });
        });
        panel.range("音量稳定", 0, 100, 1, AudioSetting.getStability(), "%", active, value -> { AudioSetting.putStability((int)value); manager.refreshAudioSetting(); });
        panel.range("对白增强", 0, 100, 1, AudioSetting.getDialogue(), "%", active, value -> { AudioSetting.putDialogue((int)value); manager.refreshAudioSetting(); });
        panel.range("音量增益", 0, 12, .5, AudioSetting.getBoost()/100.0, " dB", active, value -> { AudioSetting.putBoost((int)Math.round(value*100)); manager.refreshAudioSetting(); });
        panel.range("前级衰减", -12, 0, .5, AudioSetting.getPreamp()/100.0, " dB", active, value -> { AudioSetting.putPreamp((int)Math.round(value*100)); manager.refreshAudioSetting(); });
        panel.toggle("响度补偿", AudioSetting::isLoudnessEnabled, value -> { if (active.getAsBoolean()) { AudioSetting.putLoudness(value); manager.refreshAudioSetting(); } });
        if (manager.getAudioChannelCount() >= 2) {
            panel.choice("声道模式", new String[]{"自动", "立体声", "单声道", "左右交换"}, new int[]{0,1,2,3}, AudioSetting::getChannelMode,
                    value -> { if (active.getAsBoolean()) { AudioSetting.putChannelMode(value); manager.refreshAudioSetting(); } });
            panel.range("左右平衡", -100, 100, 1, AudioSetting.getBalance(), "", active, value -> { AudioSetting.putBalance((int)value); manager.refreshAudioSetting(); });
        }
        if (manager.getAudioChannelCount() == 6 || manager.getAudioChannelCount() == 8)
            panel.range("中置增益", 0, 12, .5, AudioSetting.getCenterGain()/100.0, " dB", active, value -> { AudioSetting.putCenterGain((int)Math.round(value*100)); manager.refreshAudioSetting(); });
        AudioEffectBands bands = manager.getAudioSettingBands();
        if (!bands.isEmpty()) {
            short[] levels = AudioSetting.getLevels(bands);
            for (int i = 0; i < bands.getCount(); i++) {
                final int band = i;
                panel.range((bands.getCenterFrequency(i)/1000) + " Hz", bands.getMinLevel()/100.0, bands.getMaxLevel()/100.0, .5, levels[i]/100.0, " dB", active, value -> {
                    levels[band] = bands.clamp((int)Math.round(value*100));
                    AudioSetting.putCustomLevels(bands, levels);
                    manager.setAudioSetting(AudioEffectPreset.CUSTOM);
                });
            }
        }
        if (manager.supportsSkipSilence()) panel.toggle("跳过静音", manager::isSkipSilence, value -> { if (active.getAsBoolean()) manager.setSkipSilenceEnabled(value); });
        panel.row("恢复原始声音", () -> { if (active.getAsBoolean()) { AudioSetting.reset(); manager.refreshAudioSetting(); } panel.dialog.dismiss(); });
        panel.dialog.show();
    }

    private static void playbackOffsets(Activity activity, PlayerManager manager, BooleanSupplier active) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, "音画／字幕同步");
        panel.row("正值延后，负值提前", null);
        panel.range("音频延迟", -10, 10, .1, manager.getAudioOffsetMs()/1000.0, " 秒", active, value -> manager.setAudioOffsetMs(Math.round(value*1000)));
        panel.range("字幕延迟", -30, 30, .1, manager.getTextOffsetMs()/1000.0, " 秒", active, value -> manager.setTextOffsetMs(Math.round(value*1000)));
        panel.row("重置同步", () -> { if (active.getAsBoolean()) { manager.setAudioOffsetMs(0); manager.setTextOffsetMs(0); } panel.dialog.dismiss(); });
        panel.dialog.show();
    }

    private static void secondarySubtitle(Activity activity, PlayerManager manager, BooleanSupplier active) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, "双字幕");
        panel.choice("副字幕", new String[]{"关闭", "自动选择"}, new int[]{-2,-1}, SubtitleSetting::getSecondaryMode,
                value -> { if (active.getAsBoolean()) { SubtitleSetting.putSecondaryMode(value); manager.setSecondarySubtitleSelection(null); manager.applySubtitleStyle(); } });
        List<androidx.media3.common.TrackSelectionOverride> candidates = manager.getSecondarySubtitleState().secondaryCandidates();
        if (!candidates.isEmpty()) panel.row("选择副字幕轨道 ›", () -> {
            String[] names = new String[candidates.size()]; int selected = -1;
            for (int i = 0; i < names.length; i++) {
                var candidate = candidates.get(i);
                var format = candidate.mediaTrackGroup.getFormat(candidate.trackIndices.get(0));
                names[i] = (format.label == null ? "字幕 " + (i + 1) : format.label) + (format.language == null ? "" : " · " + format.language);
                if (candidate.equals(manager.getSecondarySubtitleState().explicitSelection())) selected = i;
            }
            select(activity, "选择副字幕轨道", names, selected, index -> {
                if (active.getAsBoolean()) { SubtitleSetting.putSecondaryMode(-1); manager.setSecondarySubtitleSelection(candidates.get(index)); }
            });
        });
        panel.range("副字幕位置", 0, 100, 1, SubtitleSetting.getSecondaryPosition(), "%", active,
                value -> { SubtitleSetting.putSecondaryPosition((float)value); manager.applySubtitleStyle(); });
        panel.dialog.show();
    }
    private static void message(Activity activity, String title, String detail) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, title);
        panel.row(detail, null);
        panel.row("知道了", panel.dialog::dismiss);
        panel.dialog.show();
    }
    private static void confirm(Activity activity, String title, String detail, Runnable action) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, title);
        panel.row(detail, null);
        panel.row("确认", () -> {
            action.run();
            panel.dialog.dismiss();
        });
        panel.row("取消", panel.dialog::dismiss);
        panel.dialog.show();
    }
    private static void edit(Activity activity, String title, String detail, String value, String hint, Consumer<String> save) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, title);
        panel.row(detail, null);
        EditText input = new EditText(activity);
        input.setText(value);
        input.setHint(hint);
        input.setSingleLine(false);
        input.setTextColor(activity.getColor(R.color.text_foreground));
        input.setHintTextColor(activity.getColor(R.color.text_sub_foreground));
        input.setPadding(panel.dp(12), panel.dp(12), panel.dp(12), panel.dp(12));
        panel.content.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.row("保存", () -> {
            save.accept(input.getText().toString().trim());
            panel.dialog.dismiss();
        });
        panel.dialog.show();
        input.requestFocus();
        Window window = panel.dialog.getWindow();
        if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void toggle(String label, String key, boolean defaultValue) {
        TextView[] view = new TextView[1];
        Runnable render = () -> view[0].setText(label + "：" + (Hawk.get(key, defaultValue) ? "开" : "关"));
        view[0] = row(label, () -> { Hawk.put(key, !Hawk.get(key, defaultValue)); render.run(); }); render.run();
    }
    private void toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        TextView[] view = new TextView[1];
        Runnable render = () -> view[0].setText(label + "：" + (getter.getAsBoolean() ? "开" : "关"));
        view[0] = row(label, () -> { setter.accept(!getter.getAsBoolean()); render.run(); });
        render.run();
    }
    private void choice(String label, String[] names, int[] values, IntSupplier getter, IntConsumer setter) {
        TextView[] view = new TextView[1];
        Runnable render = () -> {
            int current = getter.getAsInt(), selected = 0;
            for (int i = 0; i < values.length; i++) if (values[i] == current) { selected = i; break; }
            view[0].setText(label + "：" + names[selected]);
        };
        view[0] = row(label, () -> {
            int current = getter.getAsInt(), selected = 0;
            for (int i = 0; i < values.length; i++) if (values[i] == current) { selected = i; break; }
            select(activity, label, names, selected, index -> {
                setter.accept(values[index]); render.run();
            });
        });
        render.run();
    }
    private void number(String label, String key, int[] values, String unit, int defaultValue) {
        TextView[] view = new TextView[1]; String[] names = new String[values.length]; for (int i = 0; i < values.length; i++) names[i] = values[i] + unit;
        Runnable render = () -> view[0].setText(label + "：" + Hawk.get(key, defaultValue) + unit);
        view[0] = row(label, () -> actions(activity, label, names, index -> { Hawk.put(key, values[index]); render.run(); })); render.run();
    }
    public static void danmaku(Activity activity) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, "弹幕设置");
        panel.toggle("加载弹幕", "danmaku_load", false); panel.toggle("显示弹幕", "danmaku_show", true);
        panel.toggle("自动匹配", "danmaku_auto", false); panel.toggle("优先使用源返回的弹幕", "danmaku_spider_first", true);
        TextView[] api = new TextView[1]; Runnable render = () -> api[0].setText("匹配接口：" + (Hawk.get("danmaku_api_url", "").isEmpty() ? "跟随点播源" : Hawk.get("danmaku_api_url", "")));
        api[0] = panel.row("匹配接口", () -> edit(activity, "弹幕匹配接口",
                "支持 {name}、{episode} GET 模板；无模板时发送 name、episode JSON。返回弹幕名称和地址列表。",
                Hawk.get("danmaku_api_url", ""), "接口地址，留空跟随点播源", value -> {
                    Hawk.put("danmaku_api_url", value); render.run();
                })); render.run();
        panel.number("不透明度", "danmaku_opacity", new int[]{25, 50, 75, 100}, "%", 100);
        panel.number("同屏数量上限", "danmaku_density", new int[]{10, 30, 60, 100, 150, 300, 500}, "条", 150);
        TextView[] scale = new TextView[1]; scale[0] = panel.row("文字大小：" + Hawk.get("danmaku_scale", 1f), () -> actions(activity, "文字大小",
                new String[]{"0.5", "0.75", "1.0", "1.5", "2.0", "3.0"}, index -> { float value = new float[]{.5f,.75f,1f,1.5f,2f,3f}[index]; Hawk.put("danmaku_scale", value); scale[0].setText("文字大小：" + value); }));
        TextView[] area = new TextView[1]; area[0] = panel.row("显示区域：" + (int) (Hawk.get("danmaku_area", .5f) * 100) + "%", () -> actions(activity, "显示区域",
                new String[]{"25%", "50%", "75%", "100%"}, index -> { float value = (index + 1) * .25f; Hawk.put("danmaku_area", value); area[0].setText("显示区域：" + (int) (value * 100) + "%"); }));
        TextView[] duration = new TextView[1]; duration[0] = panel.row("通过时间：" + Hawk.get("danmaku_duration", 8000L) / 1000 + "秒", () -> actions(activity, "弹幕通过时间",
                new String[]{"3秒", "5秒", "8秒", "10秒", "15秒"}, index -> { long value = new long[]{3000,5000,8000,10000,15000}[index]; Hawk.put("danmaku_duration", value); duration[0].setText("通过时间：" + value / 1000 + "秒"); }));
        TextView[] offset = new TextView[1]; offset[0] = panel.row("时间调整：" + Hawk.get("danmaku_offset", 0L) / 1000 + "秒", () -> actions(activity, "时间调整（正值延后）",
                new String[]{"-30秒", "-10秒", "-5秒", "0秒", "+5秒", "+10秒", "+30秒"}, index -> { long value = new long[]{-30000,-10000,-5000,0,5000,10000,30000}[index]; Hawk.put("danmaku_offset", value); offset[0].setText("时间调整：" + value / 1000 + "秒"); }));
        panel.dialog.show();
    }
    public static void advancedPlayback(Activity activity) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity, "高级播放设置");
        panel.row("下次播放生效", null);
        panel.toggle("下一集预加载（Media3）", com.kukuqi.tvbox.osc.util.HawkConfig.NEXT_EPISODE_PRELOAD, true);
        panel.toggle("音频直通", com.kukuqi.tvbox.engine.setting.DecodeSetting::isAudioPassThrough,
                com.kukuqi.tvbox.engine.setting.DecodeSetting::putAudioPassThrough);
        panel.toggle("静音片段跳过（Exo）", com.kukuqi.tvbox.engine.setting.SpeedSetting::isSkipSilence,
                com.kukuqi.tvbox.engine.setting.SpeedSetting::putSkipSilence);
        panel.toggle("ASS 特效字幕（Exo，下次播放生效）", PlayerSetting::isLibass, PlayerSetting::putLibass);
        panel.toggle("MPV Vulkan渲染", PlayerSetting::isMpvVulkan,
                PlayerSetting::putMpvVulkan);
        panel.toggle("MPV GPU Next", PlayerSetting::isMpvGpuNext,
                PlayerSetting::putMpvGpuNext);
        panel.choice("双字幕", new String[]{"默认关闭", "关闭", "自动选择"}, new int[]{-3, -2, -1},
                SubtitleSetting::getSecondaryMode,
                SubtitleSetting::putSecondaryMode);
        panel.choice("副字幕位置", new String[]{"顶部", "10%", "25%", "50%", "75%", "底部"}, new int[]{0, 10, 25, 50, 75, 100},
                () -> Math.round(SubtitleSetting.getSecondaryPosition()),
                value -> SubtitleSetting.putSecondaryPosition(value));
        panel.dialog.show();
    }
    public static void modules(Activity activity) {
        modules(activity, com.kukuqi.tvbox.osc.util.RuntimeDownload::catalogUrl);
    }

    /** The catalog provider lets instrumentation use a fixture without changing user preferences. */
    public static void modules(Activity activity, java.util.function.Supplier<String> catalogProvider) {
        FeatureSettingsDialog panel = new FeatureSettingsDialog(activity,
                activity.getPackageName().endsWith(".corevalidation") ? "模块管理 · 验证版" : "模块管理");
        for (com.kukuqi.tvbox.engine.runtime.RuntimeModule module : com.kukuqi.tvbox.engine.runtime.RuntimeModule.values()) {
            TextView item = panel.row("", () -> {});
            int icon = switch (module) {
                case PYTHON -> R.drawable.runtime_icon_python;
                case QUICKJS -> R.drawable.runtime_icon_quickjs;
                case THUNDER -> R.drawable.runtime_icon_thunder;
                case NODEJS -> R.drawable.runtime_icon_nodejs;
                case BDJ -> R.drawable.runtime_icon_bdj;
                case MPV -> R.drawable.runtime_icon_mpv;
            };
            android.graphics.drawable.Drawable drawable = activity.getDrawable(icon);
            drawable.setBounds(0, 0, panel.dp(36), panel.dp(36));
            item.setCompoundDrawablesRelative(drawable, null, null, null);
            item.setCompoundDrawablePadding(panel.dp(12));
            boolean[] downloading = {false};
            Runnable refresh = () -> {
                if (!downloading[0]) item.setText(module.title + " · " + com.kukuqi.tvbox.engine.runtime.RuntimeModules.status(activity, module));
            };
            refresh.run();
            panel.root.getViewTreeObserver().addOnWindowFocusChangeListener(focused -> { if (focused) refresh.run(); });
            Runnable download = () -> {
                if (downloading[0]) return;
                String catalog = catalogProvider.get();
                if (catalog.trim().isEmpty()) {
                    message(activity, "模块下载地址未配置", "请在下方设置可访问的模块目录地址后重试。");
                    return;
                }
                downloading[0] = true;
                java.util.function.Consumer<String> progress = text -> activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed() && panel.dialog.isShowing()) item.setText(module.title + " · " + text);
                });
                com.kukuqi.tvbox.osc.util.HeavyTaskUtil.executeNewTask(() -> {
                    try {
                        File apk = com.kukuqi.tvbox.osc.util.RuntimeDownload.download(activity.getApplicationContext(), module, catalog, progress);
                        activity.runOnUiThread(() -> {
                            downloading[0] = false;
                            if (activity.isFinishing() || activity.isDestroyed() || !panel.dialog.isShowing()) return;
                            if (apk == null) {
                                refresh.run();
                                Toast.makeText(activity, module.title + " 已是最新版本", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            try {
                                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
                                activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW)
                                        .setDataAndType(uri, "application/vnd.android.package-archive")
                                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION));
                            } catch (Exception failure) { progress.accept("无法打开安装器，请重试"); }
                        });
                    } catch (Exception failure) {
                        activity.runOnUiThread(() -> { downloading[0] = false; progress.accept(com.kukuqi.tvbox.osc.util.RuntimeDownload.errorMessage(failure) + "（点击重试）"); });
                    }
                });
            };
            item.setOnClickListener(v -> {
                if (downloading[0]) return;
                try {
                    com.kukuqi.tvbox.engine.runtime.RuntimeModules.installed(activity, module);
                    refresh.run();
                    actions(activity, module.title + " · " + com.kukuqi.tvbox.engine.runtime.RuntimeModules.status(activity, module),
                            new String[]{"检查更新", "系统应用信息"}, index -> {
                                if (index == 0) download.run();
                                else {
                                    try {
                                        activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                android.net.Uri.parse("package:" + module.packageName(activity))));
                                    } catch (Exception failure) { message(activity, "无法打开应用信息", "请在系统设置中查看模块。"); }
                                }
                            });
                } catch (android.content.pm.PackageManager.NameNotFoundException missing) { download.run(); }
                catch (Exception invalid) { message(activity, module.title + " 模块不可用", invalid.getMessage()); }
            });
        }
        TextView builtIn = panel.row("内置：Exo / Media3、JAR Spider", null);
        builtIn.setTextSize(13); builtIn.setMinHeight(0); builtIn.setPadding(panel.dp(12), panel.dp(8), panel.dp(12), panel.dp(8));
        TextView moduleNote = panel.row("按需下载安装；更新已加载的模块后重启应用生效", null);
        moduleNote.setTextSize(13); moduleNote.setMinHeight(0); moduleNote.setPadding(panel.dp(12), panel.dp(8), panel.dp(12), panel.dp(8));
        TextView bdjNote = panel.row("BD-J 提供 Java 运行时；完整蓝光菜单仍需适配播放器", null);
        bdjNote.setTextSize(13); bdjNote.setMinHeight(0); bdjNote.setPadding(panel.dp(12), panel.dp(8), panel.dp(12), panel.dp(8));
        panel.choice("应用更新 / 模块下载线路", new String[]{"自动", "GitHub 直连", "加速线路 1", "加速线路 2"}, new int[]{0, 1, 2, 3},
                com.kukuqi.tvbox.osc.util.RuntimeDownload::routeMode,
                value -> Hawk.put(com.kukuqi.tvbox.osc.util.RuntimeDownload.ROUTE_KEY, value));
        panel.row("模块下载地址", () -> edit(activity, "模块下载地址", "填写 HTTPS 模块目录地址（catalog.json）；留空恢复本项目 GitHub Releases。",
                com.kukuqi.tvbox.osc.util.RuntimeDownload.catalogUrl(), "https://…/catalog.json",
                value -> {
                    String url = value.trim();
                    if (!url.isEmpty() && !(url.startsWith("https://") || (com.kukuqi.tvbox.osc.BuildConfig.DEBUG && url.startsWith("http://")))) {
                        Toast.makeText(activity, "请填写 HTTPS 地址", Toast.LENGTH_LONG).show(); return;
                    }
                    if (url.isEmpty()) Hawk.delete(com.kukuqi.tvbox.osc.util.RuntimeDownload.CATALOG_KEY);
                    else Hawk.put(com.kukuqi.tvbox.osc.util.RuntimeDownload.CATALOG_KEY, url);
                }));
        File[] caches = {com.github.catvod.utils.Path.jar(), com.github.catvod.utils.Path.js(), com.github.catvod.utils.Path.py(), new File(activity.getFilesDir(), "nodejs/sites")};
        TextView cacheSize = panel.row("", null);
        Runnable updateSizes = () -> {
            long total = 0;
            for (File cache : caches) total += com.github.catvod.utils.Path.size(cache);
            cacheSize.setText("来源脚本缓存：" + android.text.format.Formatter.formatFileSize(activity, total));
        };
        updateSizes.run();
        boolean[] busy = {false};
        TextView status = panel.row("重新加载来源模块", () -> {});
        Consumer<String> finish = message -> activity.runOnUiThread(() -> {
            busy[0] = false;
            if (activity.isDestroyed() || activity.isFinishing() || !panel.dialog.isShowing()) return;
            status.setText(message);
            updateSizes.run();
        });
        status.setOnClickListener(v -> {
            if (busy[0]) return;
            busy[0] = true;
            status.setText("正在重新加载…");
            com.kukuqi.tvbox.osc.util.HeavyTaskUtil.executeNewTask(() -> {
                try {
                    ApiConfig.get().resetModules();
                    String spider = ApiConfig.get().getSpider();
                    if (spider == null || spider.isEmpty()) {
                        finish.accept("模块已重置，将在使用时加载");
                        return;
                    }
                    ApiConfig.get().loadJar(true, spider, new ApiConfig.LoadConfigCallback() {
                        public void success() { finish.accept("模块重新加载完成（点击可重载）"); }
                        public void retry() { }
                        public void error(String message) { finish.accept("加载失败，请检查来源与网络（点击重试）"); }
                    });
                } catch (Exception e) { finish.accept("加载失败，请重试"); }
            });
        });
        panel.row("清除来源脚本缓存", () -> {
            if (busy[0]) return;
            confirm(activity, "清除模块缓存", "仅清理来源脚本和 JAR 缓存，保留来源、本地文件和已安装的运行时。下次使用时重新加载。", () -> {
                if (busy[0]) return;
                busy[0] = true;
                status.setText("正在清除缓存…");
                com.kukuqi.tvbox.osc.util.HeavyTaskUtil.executeNewTask(() -> {
                    try {
                        ApiConfig.get().resetModules();
                        for (File cache : caches) com.kukuqi.tvbox.osc.util.CacheCleaner.clear(cache);
                        finish.accept("缓存已清除，点击可重载模块");
                    } catch (Exception e) { finish.accept("部分缓存未清除，请重试"); }
                });
            });
        });
        panel.dialog.show();
    }
}
