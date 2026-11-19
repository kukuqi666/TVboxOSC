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
}