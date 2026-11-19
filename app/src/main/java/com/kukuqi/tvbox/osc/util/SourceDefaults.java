package com.kukuqi.tvbox.osc.util;

import com.kukuqi.tvbox.osc.bean.Subscription;
import com.orhanobut.hawk.Hawk;
import java.util.ArrayList;
import java.util.List;

/** Apply requested sources once on upgrade, respecting subsequent user selections. */
public final class SourceDefaults {
    public static final String VOD = "http://www.饭太硬.cc/tv";
    public static final String SECOND_VOD = "http://tvbox.xn--4kq62z5rby2qupq9ub.top/";
    public static final String WALLPAPER = "http://tvbox.xn--4kq62z5rby2qupq9ub.top/";
    public static final String LIVE = "https://live.zbds.top/tv/iptv4.m3u";
    private static final String APPLIED = "source_defaults_20261009_v1";

    public static void applyOnce() {
        if (Hawk.get(APPLIED, false)) { separateLegacySources(); ensureBuiltIns(); return; }
        List<Subscription> items = Hawk.get(HawkConfig.SUBSCRIPTIONS, new ArrayList<>());
        boolean found = false;
        for (Subscription item : items) {
            boolean selected = VOD.equals(item.getUrl());
            item.setChecked(selected);
            if (selected) { item.setName("饭太硬"); found = true; }
        }
        if (!found) items.add(new Subscription("饭太硬", VOD).setBuiltIn(true).setChecked(true));
        Hawk.put(HawkConfig.SUBSCRIPTIONS, items);
        Hawk.put(HawkConfig.API_URL, VOD);
        SourceLibrary.save(SourceLibrary.LIVE, "IPTV4 直播", LIVE);
        SourceLibrary.save(SourceLibrary.WALLPAPER, "王二小", WALLPAPER);
        Hawk.put(HawkConfig.LIVE_URL, LIVE);
        Hawk.put(HawkConfig.EPG_URL, "");
        Hawk.put("wallpaper_mode", "custom");
        Hawk.put(HawkConfig.WALLPAPER_URL, WALLPAPER);
        Hawk.put(APPLIED, true);
        separateLegacySources();
        ensureBuiltIns();
    }
    private static void ensureBuiltIns() {
        migrateVodSources();
        ensureBuiltIn(SourceLibrary.LIVE, "IPTV4 直播", LIVE);
        ensureBuiltIn(SourceLibrary.WALLPAPER, "王二小", WALLPAPER);
    }
    /** 移除已下架的打包订阅，保留用户添加的地址及其当前选择。 */
    private static void migrateVodSources() {
        List<Subscription> items = Hawk.get(HawkConfig.SUBSCRIPTIONS, new ArrayList<>());
        String selected = Hawk.get(HawkConfig.API_URL, VOD);
        if (isRemovedSource(selected)) selected = VOD;
        List<Subscription> kept = new ArrayList<>();
        for (Subscription item : items) {
            if (item == null || isRemovedSource(item.getUrl())) continue;
            if (VOD.equals(item.getUrl()) || SECOND_VOD.equals(item.getUrl())) continue;
            item.setChecked(selected.equals(item.getUrl()));
            kept.add(item);
        }
        kept.add(0, new Subscription("饭太硬（在线）", VOD).setBuiltIn(true).setChecked(VOD.equals(selected)));
        kept.add(1, new Subscription("王二小（在线）", SECOND_VOD).setBuiltIn(true).setChecked(SECOND_VOD.equals(selected)));
        Hawk.put(HawkConfig.SUBSCRIPTIONS, kept);
        Hawk.put(HawkConfig.API_URL, selected);
    }
    private static boolean isRemovedSource(String url) {
        return "assets://subscriptions/r18.json".equals(url) || "assets://subscriptions/18cn.json".equals(url);
    }
    private static void ensureBuiltIn(int kind, String name, String url) {
        for (SourceLibrary.Entry entry : SourceLibrary.list(kind)) if (url.equals(entry.url)) return;
        SourceLibrary.save(kind, name, url);
    }
    private static void separateLegacySources() {
        String key = "independent_sources_20261010_v1";
        if (Hawk.get(key, false)) return;
        if (Hawk.get(HawkConfig.LIVE_URL, "").isEmpty()) {
            SourceLibrary.save(SourceLibrary.LIVE, "IPTV4 直播", LIVE);
            Hawk.put(HawkConfig.LIVE_URL, LIVE);
            Hawk.put(HawkConfig.EPG_URL, "");
        }
        if ("follow".equals(Hawk.get("wallpaper_mode", "custom"))) {
            SourceLibrary.save(SourceLibrary.WALLPAPER, "王二小", WALLPAPER);
            Hawk.put("wallpaper_mode", "custom");
            Hawk.put(HawkConfig.WALLPAPER_URL, WALLPAPER);
        }
        Hawk.put(key, true);
    }
    private SourceDefaults() {}
}
