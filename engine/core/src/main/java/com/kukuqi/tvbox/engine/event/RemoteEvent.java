package com.kukuqi.tvbox.engine.event;

import android.view.KeyEvent;

import java.util.Locale;

import org.greenrobot.eventbus.EventBus;

/**
 * Remote key forwarded from the local web page.
 *
 * <p>The browser sends a logical key such as {@code up} or {@code ok}; the App maps it onto the same
 * navigation action the physical remote produces. {@code action} stays in the payload so a future
 * key that distinguishes press, repeat and release does not need a new event type.</p>
 */
public record RemoteEvent(String key, String action) {

    /** Maps a remote key name to the Android key code the UI layer understands. */
    public static int keyCode(String key) {
        if (key == null) return KeyEvent.KEYCODE_UNKNOWN;
        return switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "up" -> KeyEvent.KEYCODE_DPAD_UP;
            case "down" -> KeyEvent.KEYCODE_DPAD_DOWN;
            case "left" -> KeyEvent.KEYCODE_DPAD_LEFT;
            case "right" -> KeyEvent.KEYCODE_DPAD_RIGHT;
            case "ok" -> KeyEvent.KEYCODE_DPAD_CENTER;
            case "back" -> KeyEvent.KEYCODE_BACK;
            case "menu" -> KeyEvent.KEYCODE_MENU;
            case "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> KeyEvent.KEYCODE_0 + Integer.parseInt(key.trim());
            default -> KeyEvent.KEYCODE_UNKNOWN;
        };
    }

    /** Builds the press event for {@link #keyCode(String)}, or {@code null} when the key is unknown. */
    public static KeyEvent toKeyEvent(String key) {
        int code = keyCode(key);
        return code == KeyEvent.KEYCODE_UNKNOWN ? null : new KeyEvent(KeyEvent.ACTION_DOWN, code);
    }

    public static void dispatch(String key, String action) {
        EventBus.getDefault().post(new RemoteEvent(key, action == null || action.isEmpty() ? "press" : action));
    }
}