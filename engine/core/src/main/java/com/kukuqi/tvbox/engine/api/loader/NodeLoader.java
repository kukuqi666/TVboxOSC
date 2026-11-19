package com.kukuqi.tvbox.engine.api.loader;

import com.github.catvod.crawler.Spider;
import com.kukuqi.tvbox.engine.App;
import com.kukuqi.tvbox.engine.runtime.NodeRuntime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** node: 路由由 index.js.md5 配置建立，普通 .js 仍交给 QuickJS。 */
public final class NodeLoader {
    private final Map<String, Spider> spiders = new ConcurrentHashMap<>();
    private String recent;
    public static boolean accepts(String api) { return api != null && api.startsWith("node:"); }
    public Spider getSpider(String key, String api, String ext) {
        return spiders.computeIfAbsent(key, ignored -> {
            Spider spider = NodeRuntime.get().client().spider(api);
            try { spider.prepare(key); spider.init(App.get(), ext); return spider; }
            catch (Exception error) {
                try { spider.destroy(); } catch (Exception ignoredError) {} finally { spider.releaseNetwork(); }
                throw new IllegalStateException("Node 来源初始化失败", error);
            }
        });
    }
    public void setRecent(String key) { recent = key; }
    public Object[] proxy(Map<String, String> params) throws Exception {
        Spider spider = spiders.get(recent == null ? "" : recent);
        return spider == null ? null : spider.proxy(params);
    }
    public void clear() {
        spiders.values().forEach(spider -> { try { spider.destroy(); } catch (Exception error) { error.printStackTrace(); } finally { spider.releaseNetwork(); } });
        spiders.clear(); recent = null;
    }
}
