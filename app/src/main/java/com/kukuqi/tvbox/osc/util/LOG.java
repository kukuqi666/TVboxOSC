package com.kukuqi.tvbox.osc.util;

import android.util.Log;

import com.kukuqi.tvbox.osc.event.LogEvent;

import org.greenrobot.eventbus.EventBus;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class LOG {
    private static String TAG = "TVBox";

    public static void e(Throwable t) {
        Log.e(TAG, t.getMessage(), t);
        com.kukuqi.tvbox.engine.server.Diagnostics.log(TAG, android.util.Log.getStackTraceString(t));
        EventBus.getDefault().post(new LogEvent(String.format("E/%s ==> ", TAG) + Log.getStackTraceString(t)));
    }

    public static void e(String tag, Throwable t) {
        Log.e(tag, t.getMessage(), t);
        com.kukuqi.tvbox.engine.server.Diagnostics.log(tag, android.util.Log.getStackTraceString(t));
        EventBus.getDefault().post(new LogEvent(String.format("E/%s ==> ", tag) + Log.getStackTraceString(t)));
    }

    public static void e(String msg) {
        Log.e(TAG, "" + msg);
        com.kukuqi.tvbox.engine.server.Diagnostics.log(TAG, msg);
        EventBus.getDefault().post(new LogEvent(String.format("E/%s ==> ", TAG) + msg));
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        com.kukuqi.tvbox.engine.server.Diagnostics.log(tag, msg);
        EventBus.getDefault().post(new LogEvent(String.format("E/%s ==> ", tag) + msg));
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
        com.kukuqi.tvbox.engine.server.Diagnostics.log(TAG, msg);
        EventBus.getDefault().post(new LogEvent(String.format("I/%s ==> ", TAG) + msg));
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
        com.kukuqi.tvbox.engine.server.Diagnostics.log(tag, msg);
        EventBus.getDefault().post(new LogEvent(String.format("I/%s ==> ", tag) + msg));
    }
}