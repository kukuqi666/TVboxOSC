package com.kukuqi.tvbox.osc.danmaku;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.util.SourceDescriptor;
import com.kukuqi.tvbox.osc.util.SourceManager;
import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;
import java.util.List;
import com.kukuqi.tvbox.engine.api.DanmakuApi;
import com.kukuqi.tvbox.engine.setting.DanmakuSetting;

public final class DanmakuController {
    private final DanmakuView view;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile String status = "未加载";
    public String status() { return status; }
    private volatile int generation;
    private static final java.util.concurrent.ExecutorService PARSER = java.util.concurrent.Executors.newSingleThreadExecutor();
    public DanmakuController(DanmakuView view) { this.view = view; }
    public void cancel() { generation++; DanmakuApi.cancel(); }
    public void clear() { cancel(); view.setTimeline(null); status = "已清空"; }
    public void load(JsonElement source, String name, String episode) {
        cancel(); view.setTimeline(null); status = "正在加载";
        if (!Hawk.get("danmaku_load", false)) { status = "已关闭"; return; }
        int id = generation;
        List<String> sources = urls(source);
        String api = Hawk.get("danmaku_api_url", "");
        if (api.isEmpty()) api = ApiConfig.get().getDanmakuApi();
        boolean auto = Hawk.get("danmaku_auto", false) && !api.isEmpty();
        final String endpoint = api;
        if (!sources.isEmpty() && (Hawk.get("danmaku_spider_first", true) || !auto)) {
            fetch(sources, 0, id, () -> { if (auto) match(endpoint, name, episode, new ArrayList<>(), id); });
        } else if (auto) match(endpoint, name, episode, sources, id);
        else fetch(sources, 0, id, () -> {});
    }
    private void match(String api, String name, String episode, List<String> sources, int id) {
        if (id != generation) return;
        try {
            DanmakuSetting.putApiUrl(api);
            DanmakuApi.search(name, episode, items -> MAIN.post(() -> {
                if (id != generation) return;
                List<String> found = new ArrayList<>();
                for (com.kukuqi.tvbox.engine.bean.Danmaku item : items) if (!item.isEmpty()) found.add(item.getUrl());
                status = "匹配地址数 " + found.size();
                found.addAll(sources);
                fetch(found, 0, id, () -> {});
            }), failure -> MAIN.post(() -> {
                if (id != generation) return;
                status = "匹配请求失败 " + failure.getClass().getSimpleName();
                fetch(sources, 0, id, () -> {});
            }));
        } catch (Exception ignored) { status = "匹配格式错误"; fetch(sources, 0, id, () -> {}); }
    }
    private void fetch(List<String> urls, int index, int id, Runnable failed) {
        if (id != generation) return;
        if (index >= urls.size()) { status += "；没有可用的弹幕"; failed.run(); return; }
        SourceManager.read(urls.get(index), (content, error) -> {
            if (id != generation) return;
            if (content == null) status = "弹幕来源读取失败：" + error;
            PARSER.execute(() -> {
                DanmakuTimeline timeline = null;
                try { if (content != null) timeline = DanmakuTimeline.parse(content); } catch (Exception ignored) {}
                DanmakuTimeline parsed = timeline;
                MAIN.post(() -> {
                    if (id != generation) return;
                    if (parsed != null && !parsed.entries.isEmpty()) { status = "已加载 " + parsed.entries.size() + " 条"; view.setTimeline(parsed); }
                    else fetch(urls, index + 1, id, failed);
                });
            });
        });
    }
    public static List<String> urls(JsonElement value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isJsonNull()) return result;
        if (value.isJsonArray()) for (JsonElement item : value.getAsJsonArray()) result.addAll(urls(item));
        else if (value.isJsonObject()) { String url = SourceDescriptor.string(value.getAsJsonObject(), "url"); if (!url.isEmpty()) result.add(url); }
        else {
            String text = value.getAsString().trim();
            if (text.startsWith("[")) { try { result.addAll(urls(JsonParser.parseString(text))); } catch (Exception ignored) {} }
            else if (!text.isEmpty()) result.add(text);
        }
        return result;
    }
}
