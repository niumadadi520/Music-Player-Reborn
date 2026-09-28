package com.mengsama.mod.mengsamanetmusic.util;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsyncIoBackpressureTest {
    @Test void saturatedPoolRejectsWithoutRunningIoOnCaller() throws Exception {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) AsyncIoExecutor.executor();
        CountDownLatch entered = new CountDownLatch(pool.getCorePoolSize()), release = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean();
        try {
            for (int i = 0; i < pool.getCorePoolSize(); i++) pool.execute(() -> {
                entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            int slots = pool.getQueue().remainingCapacity();
            for (int i = 0; i < slots; i++) pool.execute(() -> {});
            CompletableFuture<Boolean> future = AsyncIoExecutor.supplyAsync(() -> { ran.set(true); return true; });
            assertTrue(future.isCompletedExceptionally());
            assertThrows(CompletionException.class, future::join);
            assertFalse(ran.get());
        } finally { release.countDown(); }
    }
}
