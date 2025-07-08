package com.igrium.worldexport.concurrent;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Various static utility methods involving completable futures.
 */
public final class FutureUtils {
    private FutureUtils() {}

    /**
     * Create a list of futures where only a limited number of them can run
     * concurrently, New tasks aren't queued until previous ones are complete,
     * allowing other threads to still use this executor.
     *
     * @param <T>         Supplier type.
     * @param suppliers   Collection of suppliers.
     * @param executor    Executor to use
     * @param threadCount Max number of concurrent tasks. If <= 0, don't cap threads.
     * @return Array of futures.
     */
    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T>[] supplyAllAsync(Collection<? extends Supplier<T>> suppliers, Executor executor, int threadCount) {

        Supplier<T>[] supplierList = suppliers.toArray(Supplier[]::new);
        CompletableFuture<T>[] futures = new CompletableFuture[supplierList.length];

        for (int i = 0; i < futures.length; i++) {
            futures[i] = new CompletableFuture<T>();
        }

        AtomicInteger currentIndex = new AtomicInteger();

        int maxConcurrent = threadCount > 0 ? Math.min(supplierList.length, threadCount) : supplierList.length;
        for (int i = 0; i < maxConcurrent; i++) {
            executor.execute(() -> runSupplyThread(executor, supplierList, futures, currentIndex));
        }

        return futures;
    }

    private static <T> void runSupplyThread(Executor executor, Supplier<T>[] supplierList, CompletableFuture<T>[] futures, AtomicInteger currentIndex) {
        int index = currentIndex.getAndIncrement();
        if (index >= futures.length)
            return;

        CompletableFuture<T> future = futures[index];
        try {
            T returnVal = supplierList[index].get();
            future.complete(returnVal);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }

        executor.execute(() -> runSupplyThread(executor, supplierList, futures, currentIndex));
    }

    /**
     * Create a list of futures where only a limited number of them can run
     * concurrently, New tasks aren't queued until previous ones are complete,
     * allowing other threads to still use this executor.
     *
     * @param runnables   Collection of runnables.
     * @param executor    Executor to use
     * @param threadCount Max number of concurrent tasks. If <= 0, don't cap threads.
     * @return Array of futures.
     */
    public static CompletableFuture<?>[] runAllAsync(Collection<? extends Runnable> runnables, Executor executor, int threadCount) {

        Runnable[] runnableList = runnables.toArray(Runnable[]::new);
        CompletableFuture<?>[] futures = new CompletableFuture[runnableList.length];

        for (int i = 0; i < futures.length; i++) {
            futures[i] = new CompletableFuture<Object>();
        }

        AtomicInteger currentIndex = new AtomicInteger();

        int maxConcurrent = threadCount > 0 ? Math.min(runnableList.length, threadCount) : runnableList.length;
        for (int i = 0; i < maxConcurrent; i++) {
            executor.execute(() -> runRunnableThread(executor, runnableList, futures, currentIndex));
        }

        return futures;
    }

    private static void runRunnableThread(Executor executor, Runnable[] runnableList, CompletableFuture<?>[] futures, AtomicInteger currentIndex) {
        int index = currentIndex.getAndIncrement();
        if (index >= futures.length)
            return;

        CompletableFuture<?> future = futures[index];
        try {
            runnableList[index].run();
            future.complete(null);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }

        executor.execute(() -> runRunnableThread(executor, runnableList, futures, currentIndex));
    }
}
