package com.kukuqi.tvbox.engine.player.subtitle;

import android.graphics.Bitmap;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.text.Cue;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.exoplayer.text.SubtitleDecoderFactory;
import androidx.media3.extractor.text.*;
import com.kukuqi.tvbox.engine.setting.PlayerSetting;
import com.kukuqi.tvbox.engine.setting.SubtitleSetting;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Timed libass bitmap output, including positioning, vector drawing and animated overrides. */
public final class AssSubtitleDecoder extends SimpleDecoder<SubtitleInputBuffer, SubtitleOutputBuffer, SubtitleDecoderException> implements SubtitleDecoder {
    private static final long FRAME_US = 50_000;
    public static final SubtitleDecoderFactory FACTORY = new SubtitleDecoderFactory() {
        @Override public boolean supportsFormat(Format format) { return SubtitleDecoderFactory.DEFAULT.supportsFormat(format); }
        @Override public SubtitleDecoder createDecoder(Format format) {
            return MimeTypes.TEXT_SSA.equals(format.sampleMimeType) && PlayerSetting.isLibass() && AssNative.isAvailable()
                    ? new AssSubtitleDecoder(format) : SubtitleDecoderFactory.DEFAULT.createDecoder(format);
        }
    };
    private final Format format;
    private final Set<AssSubtitle> tracks = new HashSet<>();
    private long context;
    private long embeddedTrack;
    private long embeddedOriginUs;
    private List<Cue> embeddedCues = List.of();

    private AssSubtitleDecoder(Format format) {
        super(new SubtitleInputBuffer[2], new SubtitleOutputBuffer[2]);
        this.format = format;
        var config = AndroidFontConfig.prepare();
        context = AssNative.open(config == null ? null : config.getAbsolutePath(), ExternalFont.getDirectory().getAbsolutePath(), SubtitleSetting.getFontFamily());
        setInitialInputBufferSize(1024);
    }
    public static boolean isAvailable() { return AssNative.isAvailable(); }
    @Override public String getName() { return "TVbox ASS"; }
    @Override public void setPositionUs(long positionUs) {}
    @Override protected SubtitleInputBuffer createInputBuffer() { return new SubtitleInputBuffer(); }
    @Override protected SubtitleOutputBuffer createOutputBuffer() {
        return new SubtitleOutputBuffer() {
            private AssSubtitle owned;
            @Override public void setContent(long timeUs, Subtitle subtitle, long offset) {
                owned = (AssSubtitle) subtitle;
                super.setContent(timeUs, subtitle, offset);
            }
            @Override public void clear() {
                if (owned != null) { synchronized (AssSubtitleDecoder.this) { owned.close(); } owned = null; }
                super.clear();
            }
            @Override public void release() { clear(); releaseOutputBuffer(this); }
        };
    }
    @Override protected SubtitleDecoderException createUnexpectedDecodeException(Throwable failure) { return new SubtitleDecoderException("ASS render failed", failure); }
    @Override protected synchronized SubtitleDecoderException decode(SubtitleInputBuffer input, SubtitleOutputBuffer output, boolean reset) {
        try {
            if (context == 0 || input.data == null) throw new SubtitleDecoderException("ASS renderer unavailable");
            byte[] bytes = new byte[input.data.remaining()]; input.data.get(bytes);
            if (bytes.length > 8 * 1024 * 1024) throw new SubtitleDecoderException("ASS exceeds subtitle budget");
            String sample = new String(bytes, StandardCharsets.UTF_8);
            long track;
            if (format.initializationData.size() >= 2) {
                // Preserve overlapping Matroska events in one track, including layered signs and drawings.
                if (embeddedTrack == 0 || reset) embeddedOriginUs = input.timeUs;
                if (embeddedTrack == 0) embeddedTrack = AssNative.newTrack(context, format.initializationData.get(1));
                String[] fields = sample.replaceFirst("^Dialogue:\\s*", "").split(",", 11);
                if (fields.length < 11) throw new SubtitleDecoderException("Invalid Matroska ASS sample");
                String packet = String.join(",", Arrays.copyOfRange(fields, 2, fields.length));
                // Renderer timestamps include Media3's large internal clock offset. Keep libass on
                // a relative timeline and let SubtitleOutputBuffer restore the renderer offset.
                AssNative.chunk(embeddedTrack, packet.getBytes(StandardCharsets.UTF_8), (input.timeUs - embeddedOriginUs) / 1000,
                        Math.max(0, timeMs(fields[1]) - timeMs(fields[0])), reset);
                if (reset) embeddedCues = List.of();
                track = embeddedTrack;
            } else {
                track = AssNative.read(context, bytes);
            }
            if (track == 0) throw new SubtitleDecoderException("Invalid ASS script");
            AssSubtitle subtitle = new AssSubtitle(track, embeddedTrack == track);
            tracks.add(subtitle);
            output.setContent(input.timeUs, subtitle, embeddedTrack == track ? embeddedOriginUs : input.subsampleOffsetUs);
            output.shouldBeSkipped = false;
            return null;
        } catch (Exception failure) { return new SubtitleDecoderException("ASS decode failed", failure); }
    }
    private static long timeMs(String text) {
        String[] parts = text.trim().split("[:.]");
        if (parts.length != 4) throw new IllegalArgumentException("Invalid ASS timestamp");
        return Long.parseLong(parts[0])*3600000 + Long.parseLong(parts[1])*60000 + Long.parseLong(parts[2])*1000 + Long.parseLong(parts[3])*10;
    }
    @Override public void release() {
        super.release();
        synchronized (this) {
            for (AssSubtitle track : new ArrayList<>(tracks)) track.close();
            AssNative.freeTrack(embeddedTrack); embeddedTrack = 0; embeddedCues = List.of();
            AssNative.close(context); context = 0;
        }
    }
    private final class AssSubtitle implements Subtitle {
        private long track;
        private final long endUs;
        private final boolean shared;
        private List<Cue> last = List.of();
        AssSubtitle(long track, boolean shared) { this.track = track; this.shared = shared; endUs = AssNative.endMs(track) * 1000; }
        void close() { if (!shared) AssNative.freeTrack(track); track = 0; last = List.of(); tracks.remove(this); }
        @Override public int getEventTimeCount() { return (int) (endUs / FRAME_US) + 2; }
        @Override public long getEventTime(int index) {
            if (index < 0 || index >= getEventTimeCount()) throw new IndexOutOfBoundsException();
            return Math.min(index * FRAME_US, endUs + 1);
        }
        @Override public int getNextEventTimeIndex(long timeUs) {
            if (timeUs < 0) return 0;
            if (timeUs > endUs) return C.INDEX_UNSET;
            return Math.min((int) (timeUs / FRAME_US) + 1, getEventTimeCount() - 1);
        }
        @Override public List<Cue> getCues(long timeUs) {
            synchronized (AssSubtitleDecoder.this) {
                if (track == 0 || context == 0 || timeUs < 0 || timeUs > endUs) return List.of();
                int[] frame = AssNative.render(context, track, timeUs / 1000);
                if (frame == null) return shared ? embeddedCues : last;
                if (frame[2] == 0 || frame[3] == 0) { if (shared) embeddedCues = List.of(); return last = List.of(); }
                Bitmap bitmap = Bitmap.createBitmap(frame, 4, frame[2], frame[2], frame[3], Bitmap.Config.ARGB_8888);
                last = List.of(new Cue.Builder().setBitmap(bitmap).setPosition(frame[0]/1920f).setPositionAnchor(Cue.ANCHOR_TYPE_START)
                        .setLine(frame[1]/1080f, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_START)
                        .setSize(frame[2]/1920f).setBitmapHeight(frame[3]/1080f).build());
                if (shared) embeddedCues = last;
                return last;
            }
        }
    }
}
