package com.kukuqi.tvbox.osc;

import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import com.kukuqi.tvbox.osc.ui.activity.MainActivity;

/** Runtime reflection paths that cannot be discovered from ordinary Java calls. */
public class ShrinkRegressionTest extends InstrumentationTestCase {
    private boolean normalStart;
    @Override protected void setUp() throws Exception {
        super.setUp();
        getInstrumentation().runOnMainSync(() -> {
            normalStart = com.kukuqi.tvbox.osc.base.App.getInstance().isNormalStart;
            com.kukuqi.tvbox.osc.base.App.getInstance().isNormalStart = true;
        });
    }
    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> com.kukuqi.tvbox.osc.base.App.getInstance().isNormalStart = normalStart);
        super.tearDown();
    }
    public void testSettingsSelectionDialogInflatesAfterOptimization() throws Exception {
        MainActivity page = (MainActivity) getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(MainActivity.EXTRA_START_DESTINATION, R.id.navigation_dashboard));
        try {
            for (int i = 0; i < 100; i++) {
                View[] row = {null};
                getInstrumentation().runOnMainSync(() -> row[0] = page.findViewById(R.id.llHomeRec));
                if (row[0] != null) break;
                Thread.sleep(100);
            }
            getInstrumentation().runOnMainSync(() -> {
                View row = page.findViewById(R.id.llHomeRec);
                assertNotNull(row);
                row.performClick();
            });
            for (int i = 0; i < 100; i++) {
                android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if (root != null && !root.findAccessibilityNodeInfosByText("站点推荐").isEmpty()) {
                    getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
                    return;
                }
                Thread.sleep(100);
            }
            fail("Settings selection dialog did not appear");
        } finally { getInstrumentation().runOnMainSync(page::finish); }
    }

    private String shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = getInstrumentation().getUiAutomation().executeShellCommand(command);
             java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private boolean assAvailable() throws Exception {
        Class<?> adapter = Class.forName("com.kukuqi.tvbox.engine.player.subtitle.AssNative", true,
                getInstrumentation().getTargetContext().getClassLoader());
        java.lang.reflect.Method method = adapter.getDeclaredMethod("isAvailable");
        method.setAccessible(true);
        return (boolean) method.invoke(null);
    }
    public void testMissingModuleRecoversThroughHostAfterOptimization() throws Exception {
        String module = com.kukuqi.tvbox.engine.runtime.RuntimeModule.MPV.packageName(getInstrumentation().getTargetContext());
        assertNull("请在新进程单独运行", com.github.catvod.utils.RuntimeModule.MPV.getLoadedFile());
        assertTrue(shell("pm uninstall " + module).contains("Success"));
        com.kukuqi.tvbox.osc.player.CorePlayer[] player = {null};
        try {
            assertFalse(assAvailable());
            getInstrumentation().runOnMainSync(() -> {
                player[0] = new com.kukuqi.tvbox.osc.player.CorePlayer(1, null);
                player[0].initPlayer();
                assertEquals(com.kukuqi.tvbox.engine.setting.PlayerSetting.ENGINE_EXO, player[0].manager().getEngine());
                assertTrue(player[0].useRenderedSubtitles());
                player[0].release(); player[0] = null;
            });
            assertTrue(shell("pm install -r /sdcard/Download/tvbox-runtime-fixtures/mpv.apk").contains("Success"));
            assertTrue(assAvailable());
            getInstrumentation().runOnMainSync(() -> {
                player[0] = new com.kukuqi.tvbox.osc.player.CorePlayer(1, null);
                player[0].initPlayer();
                assertEquals(com.kukuqi.tvbox.engine.setting.PlayerSetting.ENGINE_MPV, player[0].manager().getEngine());
            });
        } finally {
            getInstrumentation().runOnMainSync(() -> { if (player[0] != null) player[0].release(); });
            if (!com.github.catvod.utils.RuntimeModule.MPV.isInstalled())
                assertTrue(shell("pm install -r /sdcard/Download/tvbox-runtime-fixtures/mpv.apk").contains("Success"));
        }
    }
    public void testExoPlaybackThroughCurrentUiAfterOptimization() throws Exception { playbackThroughUi(2); }
    public void testMpvPlaybackThroughCurrentUiAfterOptimization() throws Exception { playbackThroughUi(1); }
    private void playbackThroughUi(int backend) throws Exception {
        int previous = com.orhanobut.hawk.Hawk.get(com.kukuqi.tvbox.osc.util.HawkConfig.PLAY_TYPE, 2);
        com.kukuqi.tvbox.osc.ui.activity.LocalPlayActivity page = null;
        String path = "/sdcard/TVboxOSC-regression/tvbox-regression.mp4";
        com.orhanobut.hawk.Hawk.put(com.kukuqi.tvbox.osc.util.HawkConfig.PLAY_TYPE, backend);
        com.blankj.utilcode.util.SPUtils.getInstance(com.kukuqi.tvbox.osc.constant.CacheConst.VIDEO_PROGRESS_SP).put(path, 0L);
        try {
            page = (com.kukuqi.tvbox.osc.ui.activity.LocalPlayActivity) getInstrumentation().startActivitySync(
                    new Intent(getInstrumentation().getTargetContext(), com.kukuqi.tvbox.osc.ui.activity.LocalPlayActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("position", 0)
                            .putExtra("videoList", "[{\"path\":\"" + path + "\",\"displayName\":\"混淆播放验证\"}]"));
            com.kukuqi.tvbox.osc.player.MyVideoView video = page.findViewById(R.id.player);
            long end = android.os.SystemClock.elapsedRealtime() + 20000;
            boolean[] ready = {false};
            while (!ready[0] && android.os.SystemClock.elapsedRealtime() < end) {
                getInstrumentation().runOnMainSync(() -> ready[0] = video.isPlaying() && video.getCurrentPosition() > 500);
                Thread.sleep(50);
            }
            assertTrue("混淆主包没有推进播放", ready[0]);
            getInstrumentation().runOnMainSync(() -> {
                com.kukuqi.tvbox.osc.player.CorePlayer player;
                try {
                    // 父类返回类型会被混淆，按保留的宿主方法名调用，避免测试依赖旧类型描述符。
                    player = (com.kukuqi.tvbox.osc.player.CorePlayer) video.getClass().getMethod("getMediaPlayer").invoke(video);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                assertEquals(backend == 1 ? com.kukuqi.tvbox.engine.setting.PlayerSetting.ENGINE_MPV
                        : com.kukuqi.tvbox.engine.setting.PlayerSetting.ENGINE_EXO, player.manager().getEngine());
                assertFalse(player.getTrackInfo().getAudio().isEmpty());
                player.pause(); assertFalse(player.isPlaying());
                player.setSpeed(1.5f); assertEquals(1.5f, player.getSpeed(), .01f);
                player.seekTo(5000); player.start();
            });
            ready[0] = false; end = android.os.SystemClock.elapsedRealtime() + 15000;
            while (!ready[0] && android.os.SystemClock.elapsedRealtime() < end) {
                getInstrumentation().runOnMainSync(() -> ready[0] = video.isPlaying() && video.getCurrentPosition() >= 4900);
                Thread.sleep(50);
            }
            assertTrue("混淆主包未恢复定位后的播放", ready[0]);
        } finally {
            if (page != null) { var closing = page; getInstrumentation().runOnMainSync(closing::finish); }
            com.orhanobut.hawk.Hawk.put(com.kukuqi.tvbox.osc.util.HawkConfig.PLAY_TYPE, previous);
        }
    }
    public void testPersistedWorkManagerInputMergerCanBeCreated() throws Exception {
        // WorkManager stores this name in its database across app updates.
        Object merger = Class.forName("androidx.work.OverwritingInputMerger",
                true, getInstrumentation().getTargetContext().getClassLoader())
                .getDeclaredConstructor().newInstance();
        assertNotNull(merger);
    }
}
