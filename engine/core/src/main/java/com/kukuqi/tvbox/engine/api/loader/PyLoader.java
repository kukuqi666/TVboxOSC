package com.kukuqi.tvbox.engine.api.loader;

import com.kukuqi.tvbox.engine.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.runtime.PythonRuntime;
import com.github.catvod.utils.RuntimeModule;
import java.util.Map;

/** Python 来源使用配套模块的类型化接口，统一交由来源生命周期管理。 */
public class PyLoader extends SpiderLoader {
    private volatile String recent;
    @Override protected void clearReferences() { recent = null; }
    public synchronized void setRecent(String recent) { checkOpen(); this.recent = recent; }
    public Spider getSpider(String key, String api, String ext) {
        return load(key, App.get(), ext, () -> {
            PythonRuntime runtime = RuntimeModule.PYTHON.entry(PythonRuntime.class);
            runtime.initialize();
            return runtime.spider(api, key, JsLoader.host(), JsLoader.host()::getUserAgent);
        });
    }
    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = findSpider(recent);
        return spider == null ? null : spider.proxy(params);
    }
}
