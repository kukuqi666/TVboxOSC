package com.kukuqi.tvbox.osc;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.test.InstrumentationTestCase;

import com.kukuqi.tvbox.engine.player.mpv.MpvUtil;
import com.kukuqi.tvbox.engine.runtime.RuntimeModule;
import com.kukuqi.tvbox.engine.runtime.RuntimeModules;
import com.kukuqi.tvbox.engine.setting.PlayerSetting;
import com.kukuqi.tvbox.osc.player.CorePlayer;

import java.io.File;
import java.util.zip.ZipFile;

/** 验证模块缺失后的恢复、原生库配套校验，以及宿主的实际回退行为。 */
public class MpvRuntimeRegressionTest extends InstrumentationTestCase {
    private Context host;
    private File fixture;

    @Override protected void setUp() throws Exception {
        super.setUp();
        host = getInstrumentation().getTargetContext();
        fixture = new File("/sdcard/Download/tvbox-runtime-fixtures/mpv.apk");
        assertTrue("缺少配套 MPV 验证素材", fixture.isFile());
    }

    @Override protected void tearDown() throws Exception {
        try {
            try { RuntimeModules.installed(host, RuntimeModule.MPV); }
            catch (PackageManager.NameNotFoundException missing) { install(); }
        } finally { super.tearDown(); }
    }

    private String shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor descriptor = getInstrumentation().getUiAutomation().executeShellCommand(command);
             java.io.InputStream stream = new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void install() throws Exception {
        assertTrue(shell("pm install -r " + fixture.getAbsolutePath()).contains("Success"));
    }

    public void testMissingModuleDoesNotPoisonFirstLoadAndKeepsExoSubtitles() throws Exception {
        // 此用例应在新进程运行，不能先加载原生库再卸载；系统无法在进程内卸载已映射的 ELF。
        assertNull("请独立运行此用例，先结束验证版进程", com.github.catvod.utils.RuntimeModule.MPV.getLoadedFile());
        shell("pm uninstall " + RuntimeModule.MPV.packageName(host));
        assertFalse(MpvUtil.isAvailable());
        int previous = PlayerSetting.getEngine();
        CorePlayer[] player = {null};
        try {
            getInstrumentation().runOnMainSync(() -> {
                player[0] = new CorePlayer(1, null);
                player[0].initPlayer();
                assertEquals(PlayerSetting.ENGINE_EXO, player[0].manager().getEngine());
                assertTrue("回退 Exo 后仍需使用现有字幕视图", player[0].useRenderedSubtitles());
                player[0].setSubtitleEnabled(false);
                assertFalse(player[0].useRenderedSubtitles());
                player[0].setSubtitleEnabled(true);
                assertTrue(player[0].useRenderedSubtitles());
            });
        } finally {
            getInstrumentation().runOnMainSync(() -> { if (player[0] != null) player[0].release(); PlayerSetting.putEngine(previous); });
        }
        install();
        assertTrue("首次缺失不能让播放器永久缓存失败", MpvUtil.isAvailable());
        assertNotNull(com.github.catvod.utils.RuntimeModule.MPV.getLoadedFile());
        assertTrue(MpvUtil.isAvailable());
    }

    public void testDifferentNativePayloadIsRejectedBeforeLoad() throws Exception {
        PackageInfo info = host.getPackageManager().getPackageArchiveInfo(fixture.getAbsolutePath(),
                PackageManager.GET_META_DATA | PackageManager.GET_SIGNATURES);
        assertNotNull(info);
        RuntimeModules.verify(host, RuntimeModule.MPV, info);
        info.applicationInfo.metaData.putString("tvbox.runtime.payload", "different-ffmpeg-and-jni");
        try { RuntimeModules.verify(host, RuntimeModule.MPV, info); fail("不配套的 MPV 原生库被接受"); }
        catch (SecurityException expected) { assertTrue(expected.getMessage().contains("不配套")); }
        assertEquals(RuntimeModule.MPV.contract(), com.github.catvod.utils.RuntimeModule.MPV.getCompatibilityId());
    }

    public void testMpvNativeLibrariesAreSeparateAndHostKeepsExoDependencies() throws Exception {
        try (ZipFile apk = new ZipFile(host.getApplicationInfo().sourceDir)) {
            assertNull(apk.getEntry("lib/arm64-v8a/libmpv.so"));
            assertNull(apk.getEntry("lib/arm64-v8a/libplayer.so"));
            for (String name : new String[]{"libc++_shared.so", "libavutil.so", "libavcodec.so", "libavformat.so", "libffmpegJNI.so"})
                assertNotNull("宿主缺少 Exo 所需依赖：" + name, apk.getEntry("lib/arm64-v8a/" + name));
        }
        try (ZipFile apk = new ZipFile(fixture)) {
            assertNotNull(apk.getEntry("lib/arm64-v8a/libmpv.so"));
            assertNotNull(apk.getEntry("lib/arm64-v8a/libplayer.so"));
            assertNotNull(apk.getEntry("assets/mpv/provenance.json"));
            assertNull("不能把不配套的 FFmpeg 混入模块", apk.getEntry("lib/arm64-v8a/libavcodec.so"));
        }
    }
}
