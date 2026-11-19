package com.kukuqi.tvbox.engine.player.command;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser for playback commands.
 *
 * <p>Commands follow the documented player syntax: {@code set <property> <value>},
 * {@code add <property> <delta>}, {@code cycle-values <property> <v1> <v2> ...}, {@code cycle pause},
 * and {@code seek <n> [relative|absolute|absolute-percent|relative-percent]}. A leading {@code !}
 * reverses the direction of a relative seek. Parsing is pure: it validates syntax and resolves the
 * numeric form, while applying the result to a player happens elsewhere.</p>
 */
public final class PlaybackCommand {

    public enum Type { SET, ADD, CYCLE_VALUES, CYCLE_PAUSE, SEEK }

    public enum SeekMode {
        RELATIVE, ABSOLUTE, ABSOLUTE_PERCENT, RELATIVE_PERCENT;

        public static SeekMode from(String text) {
            if (text == null) return RELATIVE;
            return switch (text.toLowerCase(Locale.ROOT)) {
                case "", "relative" -> RELATIVE;
                case "absolute" -> ABSOLUTE;
                case "absolute-percent", "absolute_percent" -> ABSOLUTE_PERCENT;
                case "relative-percent", "relative_percent" -> RELATIVE_PERCENT;
                default -> throw new IllegalArgumentException("未知 seek 模式 " + text);
            };
        }
    }

    /** Properties the player can currently apply; anything else is rejected rather than ignored. */
    public enum Property {
        PAUSE, SPEED, VOLUME, SUB_DELAY, AUDIO_DELAY, LOOP_FILE, CHAPTER, VIDEO_SCALE_X, VIDEO_SCALE_Y, VIDEO_ASPECT_OVERRIDE;

        public static Property from(String text) {
            String key = text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            return switch (key) {
                case "pause" -> PAUSE;
                case "speed" -> SPEED;
                case "volume" -> VOLUME;
                case "sub-delay", "subdelay" -> SUB_DELAY;
                case "audio-delay", "audiodelay" -> AUDIO_DELAY;
                case "loop-file", "loopfile" -> LOOP_FILE;
                case "chapter" -> CHAPTER;
                case "video-scale-x", "videozoomx" -> VIDEO_SCALE_X;
                case "video-scale-y", "videozoomy" -> VIDEO_SCALE_Y;
                case "video-aspect-override", "videoaspect" -> VIDEO_ASPECT_OVERRIDE;
                default -> throw new IllegalArgumentException("未知属性 " + text);
            };
        }
    }

    private static final String REVERSE = "!";
    private static final double MIN_SPEED = 0.1;
    private static final double MAX_SPEED = 5;
    private static final double MIN_VOLUME = 0;
    private static final double MAX_VOLUME = 100;

    private final Type type;
    private final String property;
    private final List<String> values;
    private final double number;
    private final SeekMode mode;
    private final boolean reverse;

    private PlaybackCommand(Type type, @Nullable String property, List<String> values, double number, SeekMode mode, boolean reverse) {
        this.type = type;
        this.property = property;
        this.values = List.copyOf(values);
        this.number = number;
        this.mode = mode;
        this.reverse = reverse;
    }

    @Nullable
    public static PlaybackCommand parse(@Nullable String line) {
        if (line == null) return null;
        String text = line.trim();
        if (text.isEmpty()) return null;
        boolean reverse = text.startsWith(REVERSE);
        if (reverse) text = text.substring(1).trim();
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) return null;
        String head = tokens.get(0).toLowerCase(Locale.ROOT);
        return switch (head) {
            case "set" -> parseSetOrAdd(Type.SET, tokens, reverse);
            case "add" -> parseSetOrAdd(Type.ADD, tokens, reverse);
            case "cycle-values" -> parseCycleValues(tokens, reverse);
            case "cycle" -> parseCycle(tokens);
            case "seek" -> parseSeek(tokens, reverse);
            default -> throw new IllegalArgumentException("未知指令 " + head);
        };
    }

    private static PlaybackCommand parseSetOrAdd(Type type, List<String> tokens, boolean reverse) {
        if (tokens.size() < 2) throw new IllegalArgumentException(tokens.get(0) + " 缺少属性");
        if (tokens.size() < 3) throw new IllegalArgumentException(tokens.get(0) + " 缺少数值");
        Property property = Property.from(tokens.get(1));
        if (property == Property.VIDEO_ASPECT_OVERRIDE) {
            String value = tokens.get(2);
            if (value.isEmpty()) throw new IllegalArgumentException("video-aspect-override 缺少值");
            return new PlaybackCommand(type, tokens.get(1), List.of(value), 0, SeekMode.RELATIVE, reverse);
        }
        double raw = number(tokens.get(2));
        // set 的目标值必须落在范围内；add 允许负增量，范围只在应用时钳制
        if (type == Type.SET) checkRange(property, reverse ? -raw : raw);
        double number = reverse ? -raw : raw;
        return new PlaybackCommand(type, tokens.get(1), List.of(tokens.get(2)), number, SeekMode.RELATIVE, reverse);
    }

    private static PlaybackCommand parseCycleValues(List<String> tokens, boolean reverse) {
        if (tokens.size() < 3) throw new IllegalArgumentException("cycle-values 需要属性与至少一个数值");
        Property property = Property.from(tokens.get(1));
        List<String> values = new ArrayList<>(tokens.subList(2, tokens.size()));
        // video-aspect-override 接收 16:9 这样的原文，其余属性必须是数值
        if (property != Property.VIDEO_ASPECT_OVERRIDE) for (String value : values) number(value);
        if (reverse) java.util.Collections.reverse(values);
        return new PlaybackCommand(Type.CYCLE_VALUES, tokens.get(1), values, 0, SeekMode.RELATIVE, reverse);
    }

    private static PlaybackCommand parseCycle(List<String> tokens) {
        if (tokens.size() < 2) throw new IllegalArgumentException("cycle 缺少属性");
        String property = tokens.get(1).toLowerCase(Locale.ROOT);
        if (property.equals("pause")) return new PlaybackCommand(Type.CYCLE_PAUSE, "pause", List.of(), 0, SeekMode.RELATIVE, false);
        throw new IllegalArgumentException("仅支持 cycle pause");
    }

    private static PlaybackCommand parseSeek(List<String> tokens, boolean reverse) {
        if (tokens.size() < 2) throw new IllegalArgumentException("seek 缺少数值");
        double number = number(tokens.get(1));
        SeekMode mode = SeekMode.from(tokens.size() > 2 ? tokens.get(2) : "");
        return new PlaybackCommand(Type.SEEK, null, List.of(tokens.get(1)), number, mode, reverse);
    }

    private static void checkRange(Property property, double value) {
        if (property == Property.SPEED && (value < MIN_SPEED || value > MAX_SPEED)) throw new IllegalArgumentException("speed 需在 0.1 到 5 之间");
        if (property == Property.VOLUME && (value < MIN_VOLUME || value > MAX_VOLUME)) throw new IllegalArgumentException("volume 需在 0 到 100 之间");
        if ((property == Property.VIDEO_SCALE_X || property == Property.VIDEO_SCALE_Y) && value <= 0) throw new IllegalArgumentException("缩放需大于 0");
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private static double number(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("不是有效数值 " + value);
        }
    }

    public Type getType() {
        return type;
    }

    @Nullable
    public String getProperty() {
        return property;
    }

    public List<String> getValues() {
        return values;
    }

    public double getNumber() {
        return number;
    }

    public SeekMode getMode() {
        return mode;
    }

    public boolean isReverse() {
        return reverse;
    }

    /**
     * Picks the next value of a {@code cycle-values} command, wrapping around. A value the player
     * already holds is skipped so repeating the command keeps advancing.
     *
     * @return the next value in the authored list, or {@code null} when no value exists
     */
    @Nullable
    public String nextValue(String current) {
        if (values.isEmpty()) return null;
        int length = values.size();
        int index = values.indexOf(current);
        // 当前值不在列表时从第一个开始，命中时前进一格并环绕
        return values.get(index < 0 ? 0 : (index + 1) % length);
    }

    /**
     * Resolves an absolute seek target in milliseconds for a known duration.
     *
     * <p>{@code seek} counts in seconds for both the relative and absolute modes; the two percent
     * modes take a percentage of the duration instead.</p>
     */
    public long resolveSeek(long position, long duration) {
        return switch (mode) {
            case RELATIVE -> Math.max(0, position + signedMillis(number));
            case ABSOLUTE -> Math.max(0, signedMillis(number));
            case ABSOLUTE_PERCENT -> duration <= 0 ? 0 : Math.max(0, Math.round(duration * number / 100.0));
            case RELATIVE_PERCENT -> duration <= 0 ? position : Math.max(0, position + Math.round(duration * signed(number) / 100.0));
        };
    }

    private long signedMillis(double seconds) {
        return Math.round((reverse ? -seconds : seconds) * 1000);
    }

    private long signed(double value) {
        return Math.round(reverse ? -value : value);
    }
}