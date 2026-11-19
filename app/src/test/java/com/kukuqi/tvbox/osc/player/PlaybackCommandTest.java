package com.kukuqi.tvbox.osc.player;

import com.kukuqi.tvbox.engine.player.command.PlaybackCommand;

import org.junit.Test;

import static org.junit.Assert.*;

public class PlaybackCommandTest {

    @Test public void setResolvesPropertyAndValue() {
        PlaybackCommand command = PlaybackCommand.parse("set speed 1.5");
        assertEquals(PlaybackCommand.Type.SET, command.getType());
        assertEquals(PlaybackCommand.Property.SPEED, PlaybackCommand.Property.from(command.getProperty()));
        assertEquals(1.5, command.getNumber(), 0.0001);
    }

    @Test public void addKeepsSignedDelta() {
        PlaybackCommand command = PlaybackCommand.parse("add volume -5");
        assertEquals(PlaybackCommand.Type.ADD, command.getType());
        assertEquals(-5, command.getNumber(), 0.0001);
    }

    @Test(expected = IllegalArgumentException.class) public void speedOutsideRangeRejected() {
        PlaybackCommand.parse("set speed 9");
    }

    @Test(expected = IllegalArgumentException.class) public void unknownPropertyRejected() {
        PlaybackCommand.parse("set brightness 1");
    }

    @Test(expected = IllegalArgumentException.class) public void unknownCommandRejected() {
        PlaybackCommand.parse("explode now");
    }

    @Test public void cycleValuesWrapsAround() {
        PlaybackCommand command = PlaybackCommand.parse("cycle-values speed 1 1.5 2");
        assertEquals(PlaybackCommand.Type.CYCLE_VALUES, command.getType());
        assertEquals("1.5", command.nextValue("1"));
        assertEquals("1", command.nextValue("2"));
        assertEquals("1", command.nextValue("unknown"));
    }

    @Test public void cyclePauseParses() {
        PlaybackCommand command = PlaybackCommand.parse("cycle pause");
        assertEquals(PlaybackCommand.Type.CYCLE_PAUSE, command.getType());
    }

    @Test(expected = IllegalArgumentException.class) public void cycleOnlySupportsPause() {
        PlaybackCommand.parse("cycle speed");
    }

    @Test public void seekRelativeIsSigned() {
        PlaybackCommand forward = PlaybackCommand.parse("seek 30 relative");
        assertEquals(PlaybackCommand.SeekMode.RELATIVE, forward.getMode());
        assertEquals(130_000, forward.resolveSeek(100_000, 600_000));
        PlaybackCommand backward = PlaybackCommand.parse("!seek 30 relative");
        assertTrue(backward.isReverse());
        assertEquals(70_000, backward.resolveSeek(100_000, 600_000));
    }

    @Test public void seekModesResolveAgainstDuration() {
        assertEquals(1_000_000, PlaybackCommand.parse("seek 1000 absolute").resolveSeek(5_000, 600_000));
        assertEquals(300_000, PlaybackCommand.parse("seek 50 absolute-percent").resolveSeek(5_000, 600_000));
        assertEquals(350_000, PlaybackCommand.parse("seek 5 relative-percent").resolveSeek(300_000, 1_000_000));
        assertEquals(250_000, PlaybackCommand.parse("!seek 5 relative-percent").resolveSeek(300_000, 1_000_000));
    }

    @Test public void seekNeverGoesNegative() {
        assertEquals(110_000, PlaybackCommand.parse("seek 100 relative").resolveSeek(10_000, 600_000));
    }

    @Test(expected = IllegalArgumentException.class) public void unknownSeekModeRejected() {
        PlaybackCommand.parse("seek 1 sideways");
    }

    @Test public void blankLinesAreIgnored() {
        assertNull(PlaybackCommand.parse(""));
        assertNull(PlaybackCommand.parse(null));
        assertNull(PlaybackCommand.parse("   "));
    }

    @Test public void quotedValuesAreOneToken() {
        PlaybackCommand command = PlaybackCommand.parse("set video-aspect-override \"16:9\"");
        assertEquals(1, command.getValues().size());
        assertEquals("16:9", command.getValues().get(0));
    }
}