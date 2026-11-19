package com.kukuqi.tvbox.osc.core;

import com.github.catvod.net.Net;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** {@code Net.map} fans a list out over a bounded pool while preserving input order. */
public class NetMapTest {

    @Test public void mapPreservesInputOrder() throws Exception {
        Net subject = new Net();
        List<Integer> items = List.of(5, 3, 1, 4, 2);
        List<Integer> doubled = subject.map(items, (item, index) -> item * 2, 3);
        assertEquals(List.of(10, 6, 2, 8, 4), doubled);
    }

    @Test public void mapExposesIndex() throws Exception {
        Net subject = new Net();
        List<String> items = List.of("a", "b", "c");
        List<String> tagged = subject.map(items, (item, index) -> index + ":" + item);
        assertEquals(List.of("0:a", "1:b", "2:c"), tagged);
    }

    @Test public void mapHonoursWorkerLimit() throws Exception {
        Net subject = new Net();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        List<Integer> items = java.util.stream.IntStream.range(0, 12).boxed().toList();
        subject.map(items, (item, index) -> {
            int now = active.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            Thread.sleep(20);
            active.decrementAndGet();
            return item;
        }, 2);
        assertTrue("并行度不应超过限制，实测 " + peak.get(), peak.get() <= 2);
    }

    @Test public void firstFailureFailsTheWholeRun() {
        Net subject = new Net();
        try {
            subject.map(List.of(1, 2, 3), (item, index) -> {
                if (item == 2) throw new IllegalStateException("坏项目");
                return item;
            }, 1);
            fail("应抛出异常");
        } catch (IllegalStateException e) {
            assertEquals("坏项目", e.getMessage());
        } catch (Exception e) {
            fail("意外异常 " + e);
        }
    }

    @Test public void emptyListCompletesImmediately() throws Exception {
        Net subject = new Net();
        assertEquals(List.of(), subject.map(List.<Integer>of(), (item, index) -> item));
    }

    @Test(expected = IllegalArgumentException.class) public void nullMapperRejected() throws Exception {
        new Net().map(List.of(1), null);
    }
}