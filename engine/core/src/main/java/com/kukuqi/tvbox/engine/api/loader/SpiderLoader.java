package com.kukuqi.tvbox.engine.api.loader;

import android.content.Context;

import com.github.catvod.crawler.Spider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** 按参考实现管理并发加载、取消及分阶段释放。 */
abstract class SpiderLoader {

    private final Map<String, Spider> spiders = new HashMap<>();
    private final Map<String, FutureTask<Spider>> loading = new HashMap<>();
    private final Object cleanupLock = new Object();
    private volatile boolean closed;
    private boolean destroyed;

    public final void clear() {
        synchronized (cleanupLock) { throwFailure(closeLoader()); }
    }

    public final void destroy() {
        synchronized (cleanupLock) { destroyLocked(); }
    }

    private void destroyLocked() {
        Throwable failure = closeLoader();
        List<Spider> active;
        synchronized (this) {
            destroyed = true;
            active = new ArrayList<>(spiders.values());
            spiders.clear();
        }
        for (Spider spider : active) failure = destroySpider(spider, failure);
        if (!active.isEmpty()) failure = clearReferences(failure);
        throwFailure(failure);
    }

    private Throwable closeLoader() {
        List<FutureTask<Spider>> pending;
        List<Spider> active;
        synchronized (this) {
            if (closed) return null;
            closed = true;
            pending = new ArrayList<>(loading.values());
            active = new ArrayList<>(spiders.values());
            loading.clear();
        }
        for (FutureTask<Spider> task : pending) task.cancel(true);
        Throwable failure = null;
        for (Spider spider : active) failure = closeSpider(spider, failure);
        return clearReferences(failure);
    }

    private Throwable clearReferences(Throwable failure) {
        try {
            clearReferences();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return failure;
    }

    protected void clearReferences() {
    }

    protected final Spider load(String key, Context context, String ext, Callable<Spider> factory) {
        FutureTask<Spider> task;
        synchronized (this) {
            checkOpen();
            Spider spider = spiders.get(key);
            if (spider != null) return spider;
            task = loading.computeIfAbsent(key, k -> new FutureTask<>(() -> createSpider(k, context, ext, factory)));
        }
        try {
            task.run();
            return awaitSpider(task);
        } finally {
            synchronized (this) {
                if (task.isDone()) loading.remove(key, task);
            }
        }
    }

    private Spider awaitSpider(FutureTask<Spider> task) {
        try {
            Spider spider = task.get();
            checkOpen();
            return spider;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("来源加载被中断", error);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException("来源加载失败", error.getCause());
        }
    }

    protected final synchronized void checkOpen() {
        if (closed) throw new CancellationException("来源加载器已关闭");
    }

    protected final synchronized Spider findSpider(String key) {
        checkOpen();
        return spiders.get(key);
    }

    private Spider createSpider(String key, Context context, String ext, Callable<Spider> factory) throws Exception {
        Spider spider = factory.call();
        try {
            if (!closed) spider.init(context, ext);
        } catch (Exception | Error failure) {
            Throwable cleanup = closeSpider(spider, null);
            cleanup = destroySpider(spider, cleanup);
            if (cleanup != null && cleanup != failure) failure.addSuppressed(cleanup);
            throw failure;
        }
        cacheSpider(key, spider);
        return spider;
    }

    private void cacheSpider(String key, Spider spider) {
        synchronized (this) {
            if (!closed) {
                spiders.put(key, spider);
                return;
            }
        }
        retireSpider(key, spider);
    }

    private void retireSpider(String key, Spider spider) {
        Throwable failure = closeSpider(spider, null);
        boolean exiting;
        synchronized (this) {
            exiting = destroyed;
            if (!exiting) spiders.put(key, spider);
        }
        if (exiting) {
            failure = destroySpider(spider, failure);
            failure = clearReferences(failure);
        }
        CancellationException error = new CancellationException("来源加载器已关闭");
        if (failure != null) error.addSuppressed(failure);
        throw error;
    }

    private Throwable closeSpider(Spider spider, Throwable failure) {
        try {
            spider.close();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return releaseNetwork(spider, failure);
    }

    private Throwable destroySpider(Spider spider, Throwable failure) {
        try {
            spider.destroy();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return releaseNetwork(spider, failure);
    }

    private Throwable releaseNetwork(Spider spider, Throwable failure) {
        // 第三方来源可能覆盖 close/destroy 而不调用父类，宿主仍须释放共享 SDK 状态。
        try { spider.releaseNetwork(); }
        catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return failure;
    }

    private void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }
}
