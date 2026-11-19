package com.kukuqi.tvbox.osc.core;

import com.kukuqi.tvbox.engine.event.RemoteEvent;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Android framework key codes are stubbed to zero in local unit tests, so these cases only pin the
 * contract that matters here: known keys resolve, unknown and null keys do not.
 */
public class RemoteKeyTest {

    @Test public void everyDocumentedKeyResolves() {
        String[] keys = {"up", "down", "left", "right", "ok", "back", "menu", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
        for (String key : keys) {
            assertNotNull("按键 " + key + " 应可解析", RemoteEvent.toKeyEvent(key));
        }
    }

    @Test public void unknownAndNullKeysAreRejected() {
        assertNull(RemoteEvent.toKeyEvent("volume_up"));
        assertNull(RemoteEvent.toKeyEvent(""));
        assertNull(RemoteEvent.toKeyEvent(null));
    }

    @Test public void keyNamesAreCaseAndSpaceInsensitive() {
        assertNotNull(RemoteEvent.toKeyEvent("OK"));
        assertNotNull(RemoteEvent.toKeyEvent(" Back "));
    }

    @Test public void digitsStayDistinct() {
        // 每个数字映射到不同键码，避免 0-9 全部落到同一个键
        java.util.Set<Integer> codes = new java.util.HashSet<>();
        for (int i = 0; i <= 9; i++) codes.add(RemoteEvent.keyCode(String.valueOf(i)));
        assertEquals(10, codes.size());
    }
}