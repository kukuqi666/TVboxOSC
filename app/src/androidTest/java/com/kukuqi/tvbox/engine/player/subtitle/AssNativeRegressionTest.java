package com.kukuqi.tvbox.engine.player.subtitle;

import android.test.InstrumentationTestCase;
import java.nio.charset.StandardCharsets;

/** Exercises the same layered Matroska chunk protocol used by the subtitle decoder. */
public class AssNativeRegressionTest extends InstrumentationTestCase {
    public void testLayeredChunksRenderAndFlush() throws Exception {
        assertTrue(AssNative.isAvailable());
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream input = new java.io.FileInputStream("/sdcard/TVboxOSC-regression/core-effects.ass")) {
            byte[] buffer = new byte[4096]; int length;
            while ((length = input.read(buffer)) != -1) bytes.write(buffer, 0, length);
        }
        String script = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        String header = script.substring(0, script.indexOf("Dialogue:"));
        java.io.File config = new java.io.File(getInstrumentation().getTargetContext().getCacheDir(), "ass-test-fonts.conf");
        try (java.io.OutputStream output = new java.io.FileOutputStream(config)) {
            output.write("<?xml version=\"1.0\"?><fontconfig><dir>/system/fonts/</dir></fontconfig>".getBytes(StandardCharsets.UTF_8));
        }
        long context = AssNative.open(config.getAbsolutePath(), null, "sans-serif"), track = 0;
        assertTrue(context != 0);
        try {
            track = AssNative.newTrack(context, header.getBytes(StandardCharsets.UTF_8));
            assertTrue(track != 0);
            int order = 0;
            for (String line : script.split("\\n")) if (line.startsWith("Dialogue:")) {
                String[] fields = line.substring(9).trim().split(",", 10);
                String packet = order + "," + fields[0] + "," + String.join(",", java.util.Arrays.copyOfRange(fields, 3, fields.length));
                AssNative.chunk(track, packet.getBytes(StandardCharsets.UTF_8), 23, 8000, order++ == 0);
            }
            assertEquals(8023, AssNative.endMs(track));
            int[] frame = AssNative.render(context, track, 500);
            assertNotNull(frame);
            assertTrue("Layered bitmap width", frame[2] > 300);
            assertTrue("Layered bitmap height", frame[3] > 300);
            byte[] replacement = "2,0,Default,,0,0,0,,REPLACEMENT".getBytes(StandardCharsets.UTF_8);
            AssNative.chunk(track, replacement, 9000, 1000, true);
            assertEquals(10000, AssNative.endMs(track));
            int[] cleared = AssNative.render(context, track, 500);
            assertNotNull(cleared);
            assertEquals(0, cleared[2]);
            int[] resumed = AssNative.render(context, track, 9500);
            assertNotNull(resumed);
            assertTrue(resumed[2] > 0);
        } finally { AssNative.freeTrack(track); AssNative.close(context); }
    }
}
