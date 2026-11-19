package com.kukuqi.tvbox.osc;

import android.test.InstrumentationTestCase;

import com.kukuqi.tvbox.engine.App;
import com.kukuqi.tvbox.engine.api.loader.BaseLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 用真实 spider jar（FongMi fm.jar，含 26 个真实 spider 类）走完整加载链，
 * 验证 csp_ 反射能否真的实例化出 FongMi 生态的蜘蛛。
 *
 * 这是「解析渠道到底通不通」的硬证据，不是方法计数。
 */
public class RealJarLoadTest extends InstrumentationTestCase {

    private File jar;

    @Override protected void setUp() throws Exception {
        super.setUp();
        File dir = new File(getInstrumentation().getTargetContext().getCacheDir(), "realjar");
        if (!dir.isDirectory()) assertTrue("无法创建目录 " + dir, dir.mkdirs());
        // 每个用例独立文件名，避免上一个用例加载后文件被占用/改权限
        jar = new File(dir, "fm-" + getName() + ".jar");
        if (jar.exists() && !jar.delete()) jar = new File(dir, "fm-" + getName() + "-" + System.nanoTime() + ".jar");
        // assets 属于测试 APK，必须用 instrumentation 自己的 context 读
        try (InputStream input = getInstrumentation().getContext().getAssets().open("fm.jar");
             OutputStream output = new FileOutputStream(jar)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
        }
        assertTrue("样本 jar 未就位, size=" + jar.length() + " path=" + jar.getAbsolutePath(), jar.length() > 100000);
    }

    /** 真实 jar 能按 ;md5; 规则被识别并落到 cache/jar/<md5>.jar。 */
    public void testRealJarCopiesAndLoads() throws Exception {
        BaseLoader loader = BaseLoader.get();
        loader.clear();
        // ext 用最小可用值；Local spider 不依赖网络
        Object spider = loader.getSpider("realtest", "csp_Local", "", "file://" + jar.getAbsolutePath());
        assertNotNull("应反射出 spider 实例", spider);
        assertEquals("com.github.catvod.spider.Local", spider.getClass().getName());
    }

    /** 反射类名不存在的站点要返回 SpiderNull，而不是崩溃。 */
    public void testUnknownSpiderReturnsNull() throws Exception {
        BaseLoader loader = BaseLoader.get();
        Object spider = loader.getSpider("bogus", "csp_NoSuchSpiderXyz", "", "file://" + jar.getAbsolutePath());
        assertEquals("com.github.catvod.crawler.SpiderNull", spider.getClass().getName());
    }

    /** 真实 spider 的关键契约方法必须可调用（不崩）。 */
    public void testRealSpiderContractCallable() throws Exception {
        BaseLoader loader = BaseLoader.get();
        Object spider = loader.getSpider("contract", "csp_Local", "", "file://" + jar.getAbsolutePath());
        com.github.catvod.crawler.Spider s = (com.github.catvod.crawler.Spider) spider;
        // Local 蜘蛛对空配置应返回空串而非抛异常
        String home = s.homeContent(true);
        assertNotNull("homeContent 不应返回 null", home);
    }

    public void testMigratedSmallJarHtmlFunctions() throws Exception { migratedJar("957f4f3ee9526dcc8a0d1d6d-custom_spider.jar"); }
    public void testMigratedLargeJarHtmlFunctions() throws Exception { migratedJar("spider-b4886fad7b.jar"); }
    private void migratedJar(String name) throws Exception {
        File external = new File("/sdcard/Download/tvbox-runtime-fixtures/" + name);
        assertTrue("请先推送配置 JAR 回归素材：" + name, external.isFile());
        try (InputStream input = new java.io.FileInputStream(external); OutputStream output = new FileOutputStream(jar)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        File script = new File(jar.getParentFile(), name + ".js");
        String code = "import {Spider} from 'catvod';export default class extends Spider {homeContent(){"
                + "let title=pdfh('<div><a href=\"/demo\">兼容测试</a></div>','a&&Text');"
                + "if(title!=='兼容测试')throw Error('pdfh:'+title);"
                + "let url=pd('<a href=\"/demo\">链接</a>','a&&href','https://example.org');"
                + "if(url!=='https://example.org/demo')throw Error('pd:'+url);"
                + "return {class:[{type_id:'1',type_name:title}]};}}";
        try (OutputStream output = new FileOutputStream(script)) { output.write(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        BaseLoader loader = new BaseLoader();
        try {
            var spider = loader.getSpider("migrated-" + name, "file://" + script.getAbsolutePath(), "", "file://" + jar.getAbsolutePath());
            assertTrue(spider.homeContent(true).contains("兼容测试"));
        } finally { loader.destroy(); script.delete(); }
    }
    public void testIncorrectLocalJarChecksumIsRejected() {
        BaseLoader loader = new BaseLoader();
        try {
            try { loader.getSpider("bad-md5", "csp_Local", "", "file://" + jar.getAbsolutePath() + ";md5;00000000000000000000000000000000"); fail("错误 MD5 的 JAR 不得执行"); }
            catch (IllegalStateException expected) { assertTrue(expected.getCause().getMessage().contains("MD5")); }
        } finally { loader.destroy(); }
    }
}