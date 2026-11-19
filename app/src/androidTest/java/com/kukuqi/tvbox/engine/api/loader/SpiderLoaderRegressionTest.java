package com.kukuqi.tvbox.engine.api.loader;

import android.test.InstrumentationTestCase;
import com.github.catvod.crawler.Spider;
import android.content.Context;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证并发加载、失败重试及切换配置后的分阶段释放。 */
public class SpiderLoaderRegressionTest extends InstrumentationTestCase {
    private ExecutorService workers;
    @Override protected void setUp() throws Exception { super.setUp(); workers = Executors.newFixedThreadPool(3); }
    @Override protected void tearDown() throws Exception { workers.shutdownNow(); super.tearDown(); }
    private static class Loader extends SpiderLoader {
        Spider get(String key, Callable<Spider> factory) { return load(key, null, "", factory); }
    }
    private static class Source extends Spider {
        final AtomicInteger initialized = new AtomicInteger(), closed = new AtomicInteger(), destroyed = new AtomicInteger();
        @Override public void init(Context context, String ext) throws Exception { initialized.incrementAndGet(); }
        @Override public void close() { closed.incrementAndGet(); }
        @Override public void destroy() { destroyed.incrementAndGet(); }
    }
    public void testConcurrentCallersShareInitialization() throws Exception {
        Loader loader = new Loader(); Source source = new Source(); AtomicInteger factories = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Callable<Spider> factory = () -> { factories.incrementAndGet(); entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return source; };
        try {
            Future<Spider> first = workers.submit(() -> loader.get("same", factory));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            Future<Spider> second = workers.submit(() -> loader.get("same", factory));
            release.countDown();
            assertSame(source, first.get(3, TimeUnit.SECONDS)); assertSame(source, second.get(3, TimeUnit.SECONDS));
            assertEquals(1, factories.get()); assertEquals(1, source.initialized.get());
        } finally { release.countDown(); loader.destroy(); }
    }
    public void testDifferentSourcesDoNotBlockEachOther() throws Exception {
        Loader loader = new Loader(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            Future<Spider> first = workers.submit(() -> loader.get("slow", () -> { entered.countDown(); release.await(); return new Source(); }));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertNotNull(workers.submit(() -> loader.get("fast", Source::new)).get(1, TimeUnit.SECONDS));
            release.countDown(); assertNotNull(first.get(3, TimeUnit.SECONDS));
        } finally { release.countDown(); loader.destroy(); }
    }
    public void testFailedInitializationReleasesAndCanRetry() {
        Loader loader = new Loader(); Source broken = new Source() {
            @Override public void init(Context context, String ext) { throw new IllegalArgumentException("初始化失败"); }
        };
        try {
            try { loader.get("retry", () -> broken); fail("初始化失败不能缓存为空来源"); }
            catch (IllegalStateException expected) { assertTrue(expected.getCause() instanceof IllegalArgumentException); }
            assertEquals(1, broken.closed.get()); assertEquals(1, broken.destroyed.get());
            Source valid = new Source(); assertSame(valid, loader.get("retry", () -> valid));
        } finally { loader.destroy(); }
    }
    public void testOverriddenCloseStillReleasesSdkNetwork() {
        Loader loader = new Loader(); Source source = new Source(); source.prepare("overridden-close");
        loader.get("source", () -> source);
        try {
            loader.clear();
            try { source.net.session(); fail("第三方 close 未调用父类时也应释放网络"); } catch (IllegalStateException expected) {}
        } finally { loader.destroy(); }
    }
    public void testClearThenDestroyIsIdempotent() {
        Loader loader = new Loader(); Source source = new Source(); loader.get("source", () -> source);
        loader.clear(); loader.clear(); assertEquals(1, source.closed.get()); assertEquals(0, source.destroyed.get());
        try { loader.get("source", Source::new); fail("关闭后不得重新加载"); } catch (CancellationException expected) {}
        loader.destroy(); loader.destroy(); assertEquals(1, source.closed.get()); assertEquals(1, source.destroyed.get());
    }
    public void testCancelledLateFactoryCannotPublishOrLeak() throws Exception {
        Loader loader = new Loader(); Source source = new Source();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        Future<?> pending = workers.submit(() -> {
            try { loader.get("late", () -> { entered.countDown(); for (;;) { try { release.await(); break; } catch (InterruptedException ignored) {} } return source; }); }
            catch (CancellationException expected) {}
            finally { finished.countDown(); }
        });
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS)); loader.clear(); loader.destroy(); release.countDown();
            assertTrue(finished.await(3, TimeUnit.SECONDS)); pending.get(3, TimeUnit.SECONDS);
            assertEquals(0, source.initialized.get()); assertEquals(1, source.closed.get()); assertEquals(1, source.destroyed.get());
        } finally { release.countDown(); loader.destroy(); }
    }
    public void testDestroyWaitsForCloseToFinish() throws Exception {
        Loader loader = new Loader(); CountDownLatch closing = new CountDownLatch(1), release = new CountDownLatch(1);
        Source source = new Source() {
            @Override public void close() { closing.countDown(); try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } super.close(); }
        };
        loader.get("ordered", () -> source);
        try {
            Future<?> clear = workers.submit(loader::clear); assertTrue(closing.await(3, TimeUnit.SECONDS));
            Future<?> destroy = workers.submit(loader::destroy);
            try { destroy.get(100, TimeUnit.MILLISECONDS); fail("close 未完成时不能 destroy"); } catch (TimeoutException expected) {}
            assertEquals(0, source.destroyed.get()); release.countDown(); clear.get(3, TimeUnit.SECONDS); destroy.get(3, TimeUnit.SECONDS);
            assertEquals(1, source.closed.get()); assertEquals(1, source.destroyed.get());
        } finally { release.countDown(); loader.destroy(); }
    }
}
