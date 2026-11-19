package com.kukuqi.tvbox.engine.api.loader;

import com.github.catvod.crawler.Spider;
import com.kukuqi.tvbox.engine.App;
import com.kukuqi.tvbox.engine.runtime.NodeRuntime;
import java.util.Map;

/** node: 路由由 index.js.md5 配置建立，普通 .js 仍交给 QuickJS。 */
public final class NodeLoader extends SpiderLoader {
    private volatile String recent;
    public static boolean accepts(String api) { return api != null && api.startsWith("node:"); }
    public Spider getSpider(String key, String api, String ext) {
        return load(key + '\u0000' + api, App.get(), ext, () -> {
            Spider spider = NodeRuntime.get().client().spider(api);
            spider.prepare(key);
            return spider;
        });
    }
    public synchronized void setRecent(String key, String api) { checkOpen(); recent = key + '\u0000' + api; }
    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = findSpider(recent);
        return spider == null ? null : spider.proxy(params);
    }
    @Override protected void clearReferences() { recent = null; }
}
