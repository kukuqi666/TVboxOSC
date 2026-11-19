package com.kukuqi.tvbox.osc.core;

import com.kukuqi.tvbox.osc.bean.SourceBean;
import com.kukuqi.tvbox.osc.util.SearchHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 订阅的 hide 字段只在推送、代理与深链中有效，不能回流到搜索候选。
 */
public class HiddenSourceSearchTest {

    private static SourceBean source(String key, boolean hide) {
        SourceBean bean = new SourceBean();
        bean.setKey(key);
        bean.setName(key);
        bean.setSearchable(1);
        bean.setHide(hide ? 1 : 0);
        return bean;
    }

    private static HashMap<String, String> checked(String... keys) {
        HashMap<String, String> map = new HashMap<>();
        for (String key : keys) map.put(key, "1");
        return map;
    }

    private static List<SourceBean> known(SourceBean... beans) {
        List<SourceBean> list = new ArrayList<>();
        for (SourceBean bean : beans) list.add(bean);
        return list;
    }

    @Test public void savedSelectionDropsHiddenSource() {
        HashMap<String, String> sources = checked("visible", "hidden");
        SearchHelper.dropHiddenSources(sources, known(source("visible", false), source("hidden", true)));
        assertTrue(sources.containsKey("visible"));
        assertFalse(sources.containsKey("hidden"));
    }

    @Test public void unknownKeyIsKeptForLaterDiagnostics() {
        HashMap<String, String> sources = checked("stale");
        SearchHelper.dropHiddenSources(sources, known(source("visible", false)));
        // 配置里已没有这个 key，不应在这里静默吞掉，留给后续流程报告来源失效
        assertTrue(sources.containsKey("stale"));
    }

    @Test public void nonHiddenSourceSurvivesFiltering() {
        HashMap<String, String> sources = checked("a", "b", "c");
        SearchHelper.dropHiddenSources(sources, known(
                source("a", false), source("b", false), source("c", false)));
        assertEquals(3, sources.size());
    }

    @Test public void nullAndEmptySelectionAreNoOps() {
        SearchHelper.dropHiddenSources(null, known(source("hidden", true)));
        HashMap<String, String> empty = new HashMap<>();
        SearchHelper.dropHiddenSources(empty, known(source("hidden", true)));
        assertTrue(empty.isEmpty());
    }

    @Test public void nullKnownListKeepsEverything() {
        HashMap<String, String> sources = checked("a", "b");
        SearchHelper.dropHiddenSources(sources, null);
        assertEquals(2, sources.size());
    }
}