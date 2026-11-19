package com.kukuqi.tvbox.osc;

import android.test.InstrumentationTestCase;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kukuqi.tvbox.engine.api.loader.BaseLoader;
import com.kukuqi.tvbox.engine.utils.ConfigPaths;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 用真实配置（18cn.json 46 站点 / R18.json 101 站点）验证配置解析与分派，
 * 不是靠类数量说话。
 */
public class RealConfigParseTest extends InstrumentationTestCase {

    private File dir;

    @Override protected void setUp() throws Exception {
        super.setUp();
        dir = new File(getInstrumentation().getTargetContext().getCacheDir(), "realcfg");
        if (!dir.isDirectory()) assertTrue(dir.mkdirs());
    }

    private String asset(String name) throws Exception {
        try (InputStream input = getInstrumentation().getContext().getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    /** 带 BOM 的真实配置必须能解析出站点。 */
    public void testRealConfigParsesWithBom() throws Exception {
        String text = asset("18cn.json");
        assertTrue("样本应是带 BOM 的真实配置", text.length() > 10000);
        String body = text.charAt(0) == '\ufeff' ? text.substring(1) : text;
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        assertTrue("sites 应在", root.has("sites"));
        assertEquals("18cn 应有 46 个站点", 46, root.getAsJsonArray("sites").size());
    }

    /** 101 站点的 R18.json 也要能解析。 */
    public void testSecondRealConfigParses() throws Exception {
        String text = asset("R18.json");
        String body = text.charAt(0) == '\ufeff' ? text.substring(1) : text;
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        assertEquals("R18 应有 101 个站点", 101, root.getAsJsonArray("sites").size());
    }

    /** spider 的相对路径 ./ 要能被补全成绝对地址。 */
    public void testRelativeSpiderResolved() throws Exception {
        String resolved = ConfigPaths.resolve("https://example.com/config/sub.json", "./CFlLnSbJ");
        assertEquals("https://example.com/config/CFlLnSbJ", resolved);
    }

    /** 分派判定要能把真实站点分到正确引擎。 */
    public void testRealSitesDispatchCorrectly() throws Exception {
        String text = asset("18cn.json");
        String body = text.charAt(0) == '\ufeff' ? text.substring(1) : text;
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        int jar = 0, js = 0, other = 0;
        for (com.google.gson.JsonElement item : root.getAsJsonArray("sites")) {
            JsonObject site = item.getAsJsonObject();
            String api = site.has("api") ? site.get("api").getAsString() : "";
            if (api.startsWith("csp_")) jar++;
            else if (api.contains(".js")) js++;
            else other++;
        }
        assertEquals("应有 1 个 jar 站点", 1, jar);
        assertEquals("应有 1 个 js 站点", 1, js);
        assertTrue("其余是 xml/json 直取", other >= 40);
    }

    /** 真实配置归一化后，相对 api 必须变成绝对地址。 */
    public void testNormalizeRewritesRelativeApi() throws Exception {
        String text = "{\"sites\":[{\"key\":\"a\",\"api\":\"./spider.jar\"}]}";
        com.google.gson.JsonElement doc = JsonParser.parseString(text);
        ConfigPaths.normalize(doc, "https://host/dir/config.json");
        String api = doc.getAsJsonObject().getAsJsonArray("sites").get(0).getAsJsonObject().get("api").getAsString();
        assertEquals("https://host/dir/spider.jar", api);
    }
}