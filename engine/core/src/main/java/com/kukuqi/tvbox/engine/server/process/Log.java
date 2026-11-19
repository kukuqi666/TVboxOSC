package com.kukuqi.tvbox.engine.server.process;

import android.os.Build;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.kukuqi.tvbox.engine.BuildConfig;
import com.kukuqi.tvbox.engine.player.PlayerManager;
import com.kukuqi.tvbox.engine.server.Diagnostics;
import com.kukuqi.tvbox.engine.server.Nano;
import com.kukuqi.tvbox.engine.server.Server;
import com.kukuqi.tvbox.engine.server.impl.Process;
import com.kukuqi.tvbox.engine.service.PlaybackService;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Diagnostics endpoint powering {@code /logs.html}.
 *
 * <p>{@code /log/info} returns one JSON document describing the build, the device and the current
 * playback so a support request can be read without a screenshot. {@code /log} returns the captured
 * lines, capped at the documented 512 KiB export limit.</p>
 */
public class Log implements Process {

    private static final int MAX_EXPORT = 512 * 1024;

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.equals("/log") || url.startsWith("/log/");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (session.getMethod() != fi.iki.elonen.NanoHTTPD.Method.GET) {
            return Nano.error(Response.Status.METHOD_NOT_ALLOWED, "日志接口仅支持读取");
        }
        if (url.equals("/log/crash")) return crashes(session.getParms());
        if (url.equals("/log") || url.equals("/log/")) return lines(session.getParms());
        if (url.equals("/log/info")) return Nano.ok(info());
        return Nano.error("未知日志端点");
    }

    private Response crashes(Map<String, String> params) {
        android.content.Context context = com.kukuqi.tvbox.engine.App.get().getBaseContext();
        String id = params.get("id");
        Response response;
        if (id != null) {
            String text = cat.ereza.customactivityoncrash.CustomActivityOnCrash.readCrashReport(context, id);
            response = text.isEmpty() ? Nano.error(Response.Status.NOT_FOUND, "未找到崩溃报告") : Nano.ok(text);
        } else {
            JsonArray reports = new JsonArray();
            for (java.io.File file : cat.ereza.customactivityoncrash.CustomActivityOnCrash.getCrashReports(context)) {
                JsonObject report = new JsonObject();
                report.addProperty("id", file.getName());
                report.addProperty("time", file.lastModified());
                report.addProperty("bytes", file.length());
                reports.add(report);
            }
            response = Nano.ok(reports.toString());
        }
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("X-Content-Type-Options", "nosniff");
        return response;
    }

    private Response lines(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostics.Line line : Diagnostics.snapshot()) {
            if (sb.length() + line.length() > MAX_EXPORT) {
                sb.append("…（超出 512 KiB，已保留较新的日志）\n");
                break;
            }
            sb.append(line).append('\n');
        }
        return Nano.ok(cat.ereza.customactivityoncrash.CustomActivityOnCrash.redact(sb.toString()));
    }

    private String info() {
        JsonObject result = new JsonObject();
        result.addProperty("version", BuildConfig.VERSION_NAME);
        result.addProperty("versionCode", BuildConfig.VERSION_CODE);
        result.addProperty("variant", BuildConfig.FLAVOR);
        result.addProperty("buildType", BuildConfig.DEBUG ? "debug" : "release");
        result.addProperty("device", Build.MODEL);
        result.addProperty("android", Build.VERSION.RELEASE);
        result.addProperty("sdk", Build.VERSION.SDK_INT);
        result.add("abis", abis());
        result.add("playback", playback());
        result.addProperty("collection", collection());
        result.add("playbackLog", playbackLog());
        String error = Diagnostics.lastPlaybackError();
        if (error == null) result.add("lastPlaybackError", JsonNull.INSTANCE);
        else result.addProperty("lastPlaybackError", cat.ereza.customactivityoncrash.CustomActivityOnCrash.redact(error));
        return result.toString();
    }

    private JsonArray abis() {
        JsonArray result = new JsonArray();
        for (String abi : Build.SUPPORTED_ABIS) result.add(abi);
        return result;
    }

    private String collection() {
        return "lines=" + Diagnostics.size() + " service=" + PlaybackService.isRunning();
    }

    private JsonObject playback() {
        JsonObject result = new JsonObject();
        PlayerManager player = player();
        boolean active = player != null && !player.isEmpty();
        result.addProperty("active", active);
        if (!active) {
            result.addProperty("title", "");
            result.addProperty("url", "");
            return result;
        }
        result.addProperty("title", player.getMediaTitle());
        result.addProperty("position", player.getPosition());
        result.addProperty("duration", player.getDuration());
        result.addProperty("state", player.isPlaying() ? "playing" : "paused");
        result.addProperty("live", player.isLive());
        result.addProperty("engine", player.getEngine());
        // 保留播放地址主体，导出时隐藏常见凭据和查询参数。
        result.addProperty("url", cat.ereza.customactivityoncrash.CustomActivityOnCrash.redact(player.getUrl()));
        return result;
    }

    private JsonObject playbackLog() {
        JsonObject result = new JsonObject();
        PlayerManager player = player();
        if (player == null) {
            result.addProperty("available", false);
            result.addProperty("reason", "no player");
            return result;
        }
        result.addProperty("available", true);
        result.addProperty("size", player.getSizeText());
        result.addProperty("decode", player.getDecodeText());
        result.addProperty("live", player.isLive());
        result.addProperty("portrait", player.isPortrait());
        result.addProperty("hasDanmaku", player.haveDanmaku());
        result.addProperty("hasTrack", player.haveTrack(0));
        result.addProperty("subs", player.getSubs().size());
        result.addProperty("danmakus", player.getDanmakus().size());
        return result;
    }

    private PlayerManager player() {
        PlaybackService service = Server.get().getService();
        if (service == null) return null;
        PlayerManager player = service.player();
        return player == null || player.isReleased() ? null : player;
    }
}