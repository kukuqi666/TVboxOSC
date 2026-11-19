package com.kukuqi.tvbox.osc;

import android.test.InstrumentationTestCase;

import com.github.catvod.net.Net;

/**
 * Net 的内存缓存必须有容量上限，否则长时间运行会无界增长。
 * 与 FongMi NetCache 的 16MB 上限对齐。
 */
public class NetCacheLimitTest extends InstrumentationTestCase {

    private Net net;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        net = new Net();
    }

    @Override
    protected void tearDown() throws Exception {
        if (net != null) net.close();
        super.tearDown();
    }

    /** 写入大量数据后，缓存条目数应被裁剪，不能无限增长。 */
    public void testCacheEvictedBeyondLimit() throws Exception {
        // 每条 1MB 字符（约 2MB 内存），写 12 条 = 24MB，超出 16MB 上限
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < 1024 * 1024; i++) chunk.append('x');
        String blob = chunk.toString();

        int written = 12;
        for (int i = 0; i < written; i++) {
            final String value = "\"" + blob + i + "\"";
            net.cached("limit-key-" + i, "{\"ttl\":600000}", () -> value);
        }

        // 至少要能读回最近写入的那条
        assertNotNull("最近写入的应命中", net.peek("limit-key-11"));
        assertTrue("最近写入内容应完整", net.peek("limit-key-11").endsWith("11\""));

        // 最早的几条应已被淘汰（读回 null 或重新装载）
        int evicted = 0;
        for (int i = 0; i < 4; i++) {
            if (net.peek("limit-key-" + i) == null) evicted++;
        }
        assertTrue("早期条目应被淘汰，实际淘汰 " + evicted, evicted >= 1);
    }

    /** 小数据量不应被误删。 */
    public void testSmallCacheSurvives() throws Exception {
        for (int i = 0; i < 5; i++) {
            final String value = "\"" + "small-" + i + "\"";
            net.cached("small-" + i, "{\"ttl\":600000}", () -> value);
        }
        for (int i = 0; i < 5; i++) {
            assertEquals("\"" + "small-" + i + "\"", net.peek("small-" + i));
        }
    }

    /** ttl=0 应视为不缓存。 */
    public void testZeroTtlNotCached() throws Exception {
        net.cached("nottl", "{\"ttl\":0}", () -> "\"v1\"");
        assertNull("ttl=0 不应缓存", net.peek("nottl"));
    }

    /** clearCache 后必须读不到。 */
    public void testClearCacheRemovesEntry() throws Exception {
        net.cached("clr", "{\"ttl\":600000}", () -> "\"value\"");
        assertNotNull(net.peek("clr"));
        net.clearCache("clr");
        assertNull("清除后应未命中", net.peek("clr"));
    }
}