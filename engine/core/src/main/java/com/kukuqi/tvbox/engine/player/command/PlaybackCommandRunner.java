package com.kukuqi.tvbox.engine.player.command;

import com.kukuqi.tvbox.engine.player.PlayerManager;

import java.util.Locale;

import androidx.annotation.Nullable;

/**
 * Applies a parsed {@link PlaybackCommand} to the running player.
 *
 * <p>Everything goes through {@link PlayerManager} rather than a raw media3 controller so the App
 * keeps its snapshot, subtitle and speed bookkeeping consistent with a remote press.</p>
 */
public final class PlaybackCommandRunner {

    private PlaybackCommandRunner() {
    }

    /**
     * Runs one command line.
     *
     * @return an empty string on success, or the rejection reason
     */
    public static String run(PlayerManager player, @Nullable String line) {
        PlaybackCommand command;
        try {
            command = PlaybackCommand.parse(line);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        if (command == null) return "";
        if (player == null || player.isReleased()) return "播放器未就绪";
        try {
            return apply(player, command);
        } catch (RuntimeException e) {
            return String.valueOf(e.getMessage());
        }
    }

    private static String apply(PlayerManager player, PlaybackCommand command) {
        switch (command.getType()) {
            case SET -> set(player, command);
            case ADD -> add(player, command);
            case CYCLE_VALUES -> cycleValues(player, command);
            case CYCLE_PAUSE -> {
                if (player.isPlaying()) player.pause();
                else player.play();
            }
            case SEEK -> player.seekTo(command.resolveSeek(player.getPosition(), player.getDuration()));
        }
        return "";
    }

    private static void set(PlayerManager player, PlaybackCommand command) {
        PlaybackCommand.Property property = PlaybackCommand.Property.from(command.getProperty());
        String value = command.getValues().get(0).toLowerCase(Locale.ROOT);
        switch (property) {
            case PAUSE -> setPause(player, !value.equals("no") && !value.equals("false") && !value.equals("0"));
            case SPEED -> player.setSpeed((float) command.getNumber());
            case VOLUME -> setVolume(player, (float) command.getNumber() / 100f);
            case SUB_DELAY -> player.setTextOffsetMs(Math.round(command.getNumber() * 1000));
            case AUDIO_DELAY -> player.setAudioOffsetMs(Math.round(command.getNumber() * 1000));
            case LOOP_FILE -> player.setRepeatOne(!value.equals("no") && !value.equals("false") && !value.equals("0"));
            case CHAPTER -> player.seekToChapter((int) command.getNumber());
            case VIDEO_SCALE_X -> setScale(player, (float) command.getNumber(), Float.NaN);
            case VIDEO_SCALE_Y -> setScale(player, Float.NaN, (float) command.getNumber());
            case VIDEO_ASPECT_OVERRIDE -> setAspect(player, command.getValues().get(0));
        }
    }

    private static void add(PlayerManager player, PlaybackCommand command) {
        PlaybackCommand.Property property = PlaybackCommand.Property.from(command.getProperty());
        switch (property) {
            case SPEED -> player.setSpeed((float) (player.getSpeed() + command.getNumber()));
            case VOLUME -> setVolume(player, player.getVolume() + (float) command.getNumber() / 100f);
            case SUB_DELAY -> player.setTextOffsetMs(player.getTextOffsetMs() + Math.round(command.getNumber() * 1000));
            case AUDIO_DELAY -> player.setAudioOffsetMs(player.getAudioOffsetMs() + Math.round(command.getNumber() * 1000));
            default -> {
                // pause、loop-file、chapter 与缩放没有可累加的数值语义
            }
        }
    }

    private static void cycleValues(PlayerManager player, PlaybackCommand command) {
        PlaybackCommand.Property property = PlaybackCommand.Property.from(command.getProperty());
        String next = command.nextValue(current(player, property));
        if (next == null) return;
        double value;
        try {
            value = Double.parseDouble(next);
        } catch (NumberFormatException e) {
            return;
        }
        switch (property) {
            case SPEED -> player.setSpeed((float) value);
            case VOLUME -> setVolume(player, (float) value / 100f);
            case SUB_DELAY -> player.setTextOffsetMs(Math.round(value * 1000));
            case AUDIO_DELAY -> player.setAudioOffsetMs(Math.round(value * 1000));
            case PAUSE -> setPause(player, value > 0);
            case LOOP_FILE -> player.setRepeatOne(value > 0);
            case CHAPTER -> player.seekToChapter((int) value);
            case VIDEO_SCALE_X -> setScale(player, (float) value, Float.NaN);
            case VIDEO_SCALE_Y -> setScale(player, Float.NaN, (float) value);
            case VIDEO_ASPECT_OVERRIDE -> setAspect(player, next);
        }
    }

    private static String current(PlayerManager player, PlaybackCommand.Property property) {
        return switch (property) {
            case SPEED -> trim(player.getSpeed());
            case VOLUME -> trim(player.getVolume() * 100f);
            case SUB_DELAY -> trim(player.getTextOffsetMs() / 1000f);
            case AUDIO_DELAY -> trim(player.getAudioOffsetMs() / 1000f);
            case PAUSE -> player.isPlaying() ? "1" : "0";
            case LOOP_FILE -> player.isRepeatOne() ? "1" : "0";
            case VIDEO_SCALE_X -> trim(player.getVideoScaleX());
            case VIDEO_SCALE_Y -> trim(player.getVideoScaleY());
            default -> "";
        };
    }

    private static String trim(float value) {
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static void setPause(PlayerManager player, boolean pause) {
        if (pause) player.pause();
        else player.play();
    }

    private static void setVolume(PlayerManager player, float volume) {
        player.setVolume(Math.max(0f, Math.min(1f, volume)));
    }

    private static void setScale(PlayerManager player, float x, float y) {
        player.setVideoScale(x, y);
    }

    private static void setAspect(PlayerManager player, String value) {
        if (value.equals("no") || value.equals("-1") || value.equals("default")) {
            player.setVideoAspect(Float.NaN);
            return;
        }
        String[] parts = value.split("[:/]");
        if (parts.length != 2) return;
        try {
            float width = Float.parseFloat(parts[0]);
            float height = Float.parseFloat(parts[1]);
            if (width <= 0 || height <= 0) return;
            player.setVideoAspect(width / height);
        } catch (NumberFormatException ignored) {
            // 无法解析的比例保持原状
        }
    }
}