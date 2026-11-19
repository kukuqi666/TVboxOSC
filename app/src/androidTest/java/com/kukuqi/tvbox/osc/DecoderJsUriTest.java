package com.kukuqi.tvbox.osc;

import android.test.InstrumentationTestCase;

import com.kukuqi.tvbox.engine.api.Decoder;

/**
 * Decoder 对脚本体里 .js 相对引用的补全，必须与 FongMi 一致。
 * 这是多文件 JavaScript 源能否取到子脚本的关键。
 */
public class DecoderJsUriTest extends InstrumentationTestCase {

    private static final String BASE = "https://cdn.example.com/cfg/tvbox.json";

    private String decode(String json) throws Exception {
        return Decoder.decode(BASE, json);
    }

    /** "./a.js?k=v" 形式的引用要补成绝对地址，且保留查询串。 */
    public void testRelativeJsWithQueryResolved() throws Exception {
        String source = "{\"sites\":[{\"key\":\"s\",\"api\":\"./lib/spider.js?v=1\"}]}";
        String out = decode(source);
        assertTrue("应补全为绝对地址: " + out,
                out.contains("https://cdn.example.com/cfg/lib/spider.js?v=1"));
        assertFalse("不应残留相对引用: " + out, out.contains("\"./lib/"));
    }

    /** "../x.js?a" 形式要按上一级目录补全。 */
    public void testParentRelativeJsResolved() throws Exception {
        String source = "{\"sites\":[{\"key\":\"s\",\"api\":\"../other/spider.js?v=2\"}]}";
        String out = decode(source);
        assertTrue("应补全到上级目录: " + out,
                out.contains("https://cdn.example.com/other/spider.js?v=2"));
    }

    /** 多个 .js 引用必须全部补全，不能只处理第一个。 */
    public void testMultipleJsRefsAllResolved() throws Exception {
        String source = "{\"sites\":["
                + "{\"key\":\"a\",\"api\":\"./a.js?x=1\"},"
                + "{\"key\":\"b\",\"api\":\"./b.js?x=2\"},"
                + "{\"key\":\"c\",\"api\":\"../c.js?x=3\"}]}";
        String out = decode(source);
        assertTrue("a 未补全: " + out, out.contains("/cfg/a.js?x=1"));
        assertTrue("b 未补全: " + out, out.contains("/cfg/b.js?x=2"));
        assertTrue("c 未补全: " + out, out.contains("/c.js?x=3"));
        assertFalse("仍有残留相对引用: " + out, out.contains("\"./"));
    }

    /** 已补全的绝对地址不能被后续 ../ ./ 替换二次破坏。 */
    public void testResolvedUrlNotDoubleReplaced() throws Exception {
        String source = "{\"sites\":[{\"key\":\"s\",\"api\":\"./deep/dir/spider.js?t=1\"}]}";
        String out = decode(source);
        assertFalse("不应出现重复拼接: " + out,
                out.contains("https://cdn.example.com/cfg/https://"));
        assertTrue("路径应完整: " + out, out.contains("https://cdn.example.com/cfg/deep/dir/spider.js?t=1"));
    }

    /** 带 __JS1__ 占位痕迹时不能漏到最终结果里。 */
    public void testNoPlaceholderLeaks() throws Exception {
        String source = "{\"sites\":[{\"key\":\"s\",\"api\":\"./a.js?q=1\"},{\"key\":\"t\",\"api\":\"../b.js?q=2\"}]}";
        String out = decode(source);
        assertFalse("__JS1__ 泄漏: " + out, out.contains("__JS1__"));
        assertFalse("__JS2__ 泄漏: " + out, out.contains("__JS2__"));
    }

    /** 明文配置（无 ** 无 2423）走 fix，相对 spider 也要补全。 */
    public void testPlainSpiderRelativeResolved() throws Exception {
        String source = "{\"spider\":\"./spider.jar\",\"sites\":[{\"key\":\"s\",\"name\":\"S\",\"type\":3,\"api\":\"csp_Test\"}]}";
        String out = decode(source);
        assertTrue("spider 应补全: " + out,
                out.contains("https://cdn.example.com/cfg/spider.jar"));
    }
}