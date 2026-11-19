package com.kukuqi.tvbox.osc.core;

import com.google.gson.Gson;
import com.kukuqi.tvbox.engine.bean.Site;

import org.junit.Test;

import static org.junit.Assert.*;

public class SiteLangTest {

    private static Site site(String json) {
        return new Gson().fromJson(json, Site.class);
    }

    @Test public void explicitTwConvertsSimplifiedToTraditional() {
        Site site = site("{\"key\":\"a\",\"name\":\"A\",\"lang\":\"zh_tw\"}");
        assertTrue(site.hasLang());
        assertTrue(site.isLangTw());
        assertEquals("戲劇", site.toSearchKey("戏剧"));
    }

    @Test public void explicitCnConvertsTraditionalToSimplified() {
        Site site = site("{\"key\":\"a\",\"name\":\"A\",\"lang\":\"zh_cn\"}");
        assertTrue(site.isLangCn());
        assertEquals("戏剧", site.toSearchKey("戲劇"));
    }

    @Test public void otherLangKeepsKeywordAsTyped() {
        Site site = site("{\"key\":\"a\",\"name\":\"A\",\"lang\":\"en\"}");
        assertTrue(site.hasLang());
        assertEquals("戏剧", site.toSearchKey("戏剧"));
    }

    @Test public void emptyLangPresentsEmptyKeyword() {
        Site site = site("{\"key\":\"a\",\"name\":\"A\"}");
        assertFalse(site.hasLang());
        assertEquals("", site.toSearchKey(""));
        assertEquals("", site.toSearchKey(null));
    }

    @Test public void hideAndIndexAndTimeoutAreRead() {
        Site hidden = site("{\"key\":\"a\",\"name\":\"A\",\"hide\":1,\"indexs\":1,\"timeout\":90}");
        assertTrue(hidden.isHide());
        assertTrue(hidden.isIndex());
        assertEquals(90_000, hidden.getTimeout());
        Site visible = site("{\"key\":\"b\",\"name\":\"B\"}");
        assertFalse(visible.isHide());
        assertFalse(visible.isIndex());
    }

    @Test public void timeoutBelowOneSecondIsClamped() {
        Site site = site("{\"key\":\"a\",\"name\":\"A\",\"timeout\":0}");
        assertEquals(1_000, site.getTimeout());
    }

    @Test public void langIsCaseAndSeparatorInsensitive() {
        assertTrue(site("{\"key\":\"a\",\"name\":\"A\",\"lang\":\"ZH-TW\"}").isLangTw());
        assertTrue(site("{\"key\":\"a\",\"name\":\"A\",\"lang\":\"tw\"}").isLangTw());
        assertTrue(site("{\"key\":\"a\",\"name\":\"A\",\"lang\":\"CN\"}").isLangCn());
    }
}