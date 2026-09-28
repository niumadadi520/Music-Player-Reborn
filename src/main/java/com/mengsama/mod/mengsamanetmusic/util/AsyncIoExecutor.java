package com.mengsama.mod.mengsamanetmusic.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

 
public final class AsyncIoExecutor {
    private static final int THREADS = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    private static final int QUEUE_CAPACITY = 256;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final ExecutorService INSTANCE = new ThreadPoolExecutor(
            THREADS, THREADS, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            new IoThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    private AsyncIoExecutor() {}

    public static ExecutorService executor() {
        return INSTANCE;
    }

    public static <T> java.util.concurrent.CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> task) {
        return supplyAsync(task, INSTANCE);
    }

    public static <T> java.util.concurrent.CompletableFuture<T> supplyAsync(
            java.util.function.Supplier<T> task, java.util.concurrent.Executor executor) {
        try {
            return java.util.concurrent.CompletableFuture.supplyAsync(task, executor);
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            return java.util.concurrent.CompletableFuture.failedFuture(busy);
        }
    }

    private static final class IoThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "MengSamaNetMusic-IO-" + THREAD_NUMBER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
