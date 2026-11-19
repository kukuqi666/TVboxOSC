package com.kukuqi.tvbox.engine.server;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Ring buffer behind {@code /log/info} and {@code /logs.html}.
 *
 * <p>Only the last few hundred lines are kept, and lines are timestamped when they arrive so a
 * viewer can tell how far the capture window reaches. The buffer is bounded by both line count and
 * character count because a single stack trace can be large.</p>
 */
public final class Diagnostics {

    private static final int MAX_LINES = 2000;
    private static final int MAX_CHARS = 512 * 1024;

    private static final Deque<Line> LINES = new ArrayDeque<>();
    private static int chars;
    private static volatile String lastPlaybackError;

    private Diagnostics() {
    }

    public static void log(String tag, String message) {
        if (message == null) return;
        append(now(), tag, message);
    }

    public static void error(String tag, @Nullable Throwable error) {
        if (error == null) return;
        java.io.StringWriter writer = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(writer));
        append(now(), tag, writer.toString());
    }

    public static void playbackError(String message) {
        if (TextUtils.isEmpty(message)) return;
        lastPlaybackError = now() + " " + message;
        log("playback", message);
    }

    @Nullable
    public static String lastPlaybackError() {
        return lastPlaybackError;
    }

    /** Snapshot copy; callers render it without holding the buffer lock. */
    public static List<Line> snapshot() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }

    public static int size() {
        synchronized (LINES) {
            return LINES.size();
        }
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
            chars = 0;
        }
    }

    /** 崩溃线程只读取已缓存状态，避免等待可能已经出错的播放器和主线程。 */
    public static String crashContext() {
        StringBuilder report = new StringBuilder();
        report.append("运行时长（毫秒）: ").append(android.os.SystemClock.elapsedRealtime() - STARTED).append('\n');
        report.append("最近播放错误: ").append(lastPlaybackError).append('\n');
        List<Line> lines = snapshot();
        StringBuilder recent = new StringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).toString();
            int remaining = 32 * 1024 - recent.length();
            if (remaining <= 0) break;
            if (line.length() > remaining) line = line.substring(line.length() - remaining);
            recent.insert(0, line + "\n");
        }
        return report.append("近期诊断日志:\n").append(recent).toString();
    }

    private static final long STARTED = android.os.SystemClock.elapsedRealtime();

    private static void append(String time, String tag, String message) {
        if (message.length() > 16 * 1024) {
            message = message.substring(0, 8 * 1024) + "\n（单条日志已截断）\n" + message.substring(message.length() - 8 * 1024);
        }
        if (tag != null && tag.length() > 128) tag = tag.substring(0, 128);
        Line line = new Line(time, tag == null ? "" : tag, message);
        int length = line.length();
        synchronized (LINES) {
            LINES.addLast(line);
            chars += length;
            while (LINES.size() > MAX_LINES || chars > MAX_CHARS) {
                Line removed = LINES.pollFirst();
                if (removed == null) break;
                chars -= removed.length();
            }
        }
    }

    private static String now() {
        return String.format(Locale.US, "%1$tH:%1$tM:%1$tS", System.currentTimeMillis());
    }

    public record Line(String time, String tag, String message) {

        public int length() {
            return time.length() + tag.length() + message.length() + 4;
        }

        @Override
        public String toString() {
            return time + " " + tag + ": " + message;
        }
    }
}