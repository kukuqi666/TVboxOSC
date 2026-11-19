package com.kukuqi.tvbox.engine.server.process;

import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kukuqi.tvbox.engine.bean.Danmaku;
import com.kukuqi.tvbox.engine.bean.Sub;
import com.kukuqi.tvbox.engine.player.PlayerManager;
import com.kukuqi.tvbox.engine.server.Nano;
import com.kukuqi.tvbox.engine.server.Server;
import com.kukuqi.tvbox.engine.server.impl.Process;
import com.kukuqi.tvbox.engine.service.PlaybackService;

import java.util.List;
import java.util.Map;

import androidx.media3.common.Player;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Remote control surface of the local web page.
 *
 * <p>{@code /remote} reports the playing media and accepts transport commands, {@code /remote/key}
 * forwards a TV remote key, and the remaining routes expose artwork, subtitles and danmaku lists so a
 * phone on the same network can drive the box without a physical remote.</p>
 */
public class Remote implements Process {

    private static final int ERR_BAD_REQUEST = 400;
    private static final int ERR_FORBIDDEN = 403;
    private static final int ERR_METHOD = 405;
    private static final int ERR_CONFLICT = 409;
    private static final int ERR_UNAVAILABLE = 503;

    private static final List<String> COMMANDS = List.of(
            "play", "pause", "stop", "prev", "next", "repeat_on", "repeat_off", "seek", "speed", "command"
    );

    private static final List<String> KEYS = List.of(
            "up", "down", "left", "right", "ok", "back", "menu",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
    );

    private static final List<String> ACTIONS = List.of("press", "down", "repeat", "up", "cancel");

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.equals("/remote") || url.startsWith("/remote/");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (url.startsWith("/remote/artwork")) return artwork(session.getParms());
        if (url.startsWith("/remote/key")) return key(session);
        if (url.startsWith("/remote/subtitle")) return subtitle(session);
        if (url.startsWith("/remote/danmaku")) return danmaku(session);
        if (url.startsWith("/remote/info")) return info();
        return remote(session);
    }

    private Response remote(IHTTPSession session) {
        // 先校验请求本身，再判断播放器是否就绪，否则参数错误会被误报成「播放器未就绪」
        if (session.getMethod() != Method.GET && session.getMethod() != Method.HEAD && session.getMethod() != Method.POST)
            return fail(ERR_METHOD, "只接受 GET 或 POST");
        Map<String, String> params = session.getParms();
        String command = params.get("command");
        boolean post = session.getMethod() == Method.POST;
        if (post && TextUtils.isEmpty(command)) return fail(ERR_BAD_REQUEST, "缺少 command");
        if (post && command.trim().equals("command") && TextUtils.isEmpty(params.get("line"))) return fail(ERR_BAD_REQUEST, "command 需要 line");
        if (post && !COMMANDS.contains(command.trim())) return fail(ERR_BAD_REQUEST, "未知命令 " + command.trim());
        PlayerManager player = getPlayer();
        if (player == null) return fail(ERR_UNAVAILABLE, "播放器未就绪");
        if (!post) return Nano.ok(state(player));
        Response rejected = apply(player, command.trim(), params);
        return rejected != null ? rejected : Nano.ok(state(player));
    }

    /** Command name and arguments are already validated by {@link #remote}; this only applies them. */
    private Response apply(PlayerManager player, String command, Map<String, String> params) {
        switch (command) {
            case "play" -> player.play();
            case "pause" -> player.pause();
            case "stop" -> stop();
            case "prev" -> prev();
            case "next" -> next();
            case "repeat_on" -> player.setRepeatOne(true);
            case "repeat_off" -> player.setRepeatOne(false);
            case "seek" -> {
                Long position = parseLong(params.get("position"));
                if (position == null) return fail(ERR_BAD_REQUEST, "seek 需要 position（毫秒）");
                player.seekTo(Math.max(0, position));
            }
            case "command" -> {
                String line = params.get("line");
                if (TextUtils.isEmpty(line)) return fail(ERR_BAD_REQUEST, "command 需要 line");
                PlaybackService service = Server.get().getService();
                if (service == null) return fail(ERR_UNAVAILABLE, "播放器未就绪");
                String error = service.runPlaybackCommand(line);
                if (!error.isEmpty()) return fail(ERR_BAD_REQUEST, error);
            }
            case "speed" -> {
                Double speed = parseDouble(params.get("speed"));
                if (speed == null) return fail(ERR_BAD_REQUEST, "speed 需要数值");
                if (speed < 0.1 || speed > 5) return fail(ERR_BAD_REQUEST, "speed 需在 0.1 到 5 之间");
                player.setSpeed(speed.floatValue());
            }
            default -> {
                return fail(ERR_BAD_REQUEST, "未知命令 " + command);
            }
        }
        return null;
    }

    private Response info() {
        PlayerManager player = getPlayer();
        JsonObject result = new JsonObject();
        result.addProperty("active", player != null && !player.isEmpty());
        result.addProperty("service", PlaybackService.isRunning());
        if (player != null) result.add("video", video(player));
        return Nano.ok(result.toString());
    }

    private Response artwork(Map<String, String> params) {
        PlayerManager player = getPlayer();
        if (player == null) return fail(ERR_UNAVAILABLE, "播放器未就绪");
        String key = params.get("key");
        if (TextUtils.isEmpty(key)) return fail(ERR_BAD_REQUEST, "缺少 key");
        if (!key.equals(player.getKey())) return fail(ERR_CONFLICT, "封面已失效");
        String artwork = player.getMetadata().artworkUri == null ? "" : player.getMetadata().artworkUri.toString();
        if (artwork.isEmpty()) return fail(ERR_CONFLICT, "当前媒体没有封面");
        return Nano.ok(artwork);
    }

    private Response key(IHTTPSession session) {
        if (session.getMethod() != Method.POST) return fail(ERR_METHOD, "只接受 POST");
        Map<String, String> params = session.getParms();
        String key = params.get("key");
        String action = params.getOrDefault("action", "press");
        String id = params.get("id");
        if (TextUtils.isEmpty(key)) return fail(ERR_BAD_REQUEST, "缺少 key");
        if (!KEYS.contains(key)) return fail(ERR_BAD_REQUEST, "未知按键 " + key);
        if (!ACTIONS.contains(action)) return fail(ERR_BAD_REQUEST, "未知动作 " + action);
        if (id != null && (id.isEmpty() || id.length() > 64)) return fail(ERR_BAD_REQUEST, "id 需为 1 到 64 个字符");
        if (!PlaybackService.isRunning()) return fail(ERR_UNAVAILABLE, "播放器未就绪");
        // 遥控按键由 App 侧的导航层消费，这里只做校验与转发
        com.kukuqi.tvbox.engine.event.RemoteEvent.dispatch(key, action);
        return Nano.ok("{}");
    }

    private Response subtitle(IHTTPSession session) {
        PlayerManager player = getPlayer();
        if (player == null) return fail(ERR_UNAVAILABLE, "播放器未就绪");
        if (session.getMethod() == Method.POST) {
            Map<String, String> params = session.getParms();
            String url = params.get("url");
            if (TextUtils.isEmpty(url)) return fail(ERR_BAD_REQUEST, "缺少 url");
            Sub sub = Sub.from(params.getOrDefault("name", ""), url, params.getOrDefault("lang", ""), params.getOrDefault("format", ""));
            player.setSub(sub);
            return Nano.ok("{}");
        }
        JsonArray items = new JsonArray();
        for (Sub sub : player.getSubs()) {
            if (sub == null || sub.isEmpty()) continue;
            JsonObject item = new JsonObject();
            item.addProperty("name", sub.getName());
            item.addProperty("url", sub.getUrl());
            item.addProperty("lang", sub.getLang());
            item.addProperty("format", sub.getFormat());
            items.add(item);
        }
        JsonObject result = new JsonObject();
        result.add("subtitles", items);
        return Nano.ok(result.toString());
    }

    private Response danmaku(IHTTPSession session) {
        PlayerManager player = getPlayer();
        if (player == null) return fail(ERR_UNAVAILABLE, "播放器未就绪");
        if (session.getMethod() == Method.POST) {
            Map<String, String> params = session.getParms();
            String url = params.get("url");
            if (TextUtils.isEmpty(url)) return fail(ERR_BAD_REQUEST, "缺少 url");
            player.setDanmaku(Danmaku.from(params.getOrDefault("name", ""), url));
            return Nano.ok("{}");
        }
        JsonArray items = new JsonArray();
        for (Danmaku item : player.getDanmakus()) {
            if (item == null || item.isEmpty()) continue;
            JsonObject json = new JsonObject();
            json.addProperty("name", item.getName());
            json.addProperty("url", item.getUrl());
            json.addProperty("selected", item.isSelected());
            items.add(json);
        }
        JsonObject result = new JsonObject();
        result.add("danmaku", items);
        return Nano.ok(result.toString());
    }

    private String state(PlayerManager player) {
        if (player.isReleased()) return "{}";
        JsonObject result = new JsonObject();
        result.addProperty("active", !player.isEmpty());
        result.addProperty("title", player.getMediaTitle());
        result.addProperty("mediaKey", player.getKey());
        result.addProperty("artwork", player.getMetadata().artworkUri == null ? "" : player.getMetadata().artworkUri.toString());
        result.addProperty("position", player.getPosition());
        result.addProperty("duration", player.getDuration());
        result.addProperty("state", player.isPlaying() ? "playing" : "paused");
        result.addProperty("playWhenReady", player.isPlaying());
        result.addProperty("repeatOne", player.isRepeatOne());
        result.addProperty("live", player.isLive());
        result.addProperty("canNavigate", true);
        result.addProperty("canSeek", !player.isLive());
        result.addProperty("canSetSpeed", !player.isLive());
        result.addProperty("speed", player.getSpeed());
        result.addProperty("engine", player.getEngine());
        result.add("video", video(player));
        return result.toString();
    }

    private JsonObject video(PlayerManager player) {
        JsonObject result = new JsonObject();
        result.addProperty("width", player.getVideoWidth());
        result.addProperty("height", player.getVideoHeight());
        result.addProperty("live", player.isLive());
        result.addProperty("url", player.getUrl());
        return result;
    }

    private void stop() {
        PlaybackService service = Server.get().getService();
        if (service != null) service.dispatchStop();
    }

    private void prev() {
        PlaybackService service = Server.get().getService();
        if (service != null) service.dispatchPrev();
    }

    private void next() {
        PlaybackService service = Server.get().getService();
        if (service != null) service.dispatchNext();
    }

    private PlayerManager getPlayer() {
        PlaybackService service = Server.get().getService();
        if (service == null) return null;
        PlayerManager player = service.player();
        return player == null || player.isReleased() ? null : player;
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        try {
            return value == null ? null : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Response fail(int code, String message) {
        return Nano.error(Response.Status.lookup(code) == null ? Response.Status.INTERNAL_ERROR : Response.Status.lookup(code), message);
    }
}