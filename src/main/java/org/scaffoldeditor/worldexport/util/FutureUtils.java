package org.scaffoldeditor.worldexport.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Various utility functions relating to {@link Future} and {@link CompletableFuture}.
 */
public final class FutureUtils {
    private FutureUtils() {};

    /**
     * A Supplier that may throw exceptions.
     * @see Supplier
     */
    public static interface DangerousSupplier<T> {
        /**
         * Gets a result.
         * @return a result
         * @throws Exception If an exception is thrown.
         */
        T get() throws Exception;
    }

    /**
     * A runnable that may throw exceptions.
     * @see Runnable
     */
    public static interface DangerousRunnable {
        /**
         * Execute the code.
         * @throws Exception If an exception is thrown.
         */
        void run() throws Exception;
    }

    public static <T> CompletableFuture<T> supplyAsync(DangerousSupplier<T> supplier, Executor executor) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static CompletableFuture<Void> runAsync(DangerousRunnable runnable, Executor executor) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
