package org.scaffoldeditor.worldexport.util;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    /**
     * Wait up to five seconds for this future to return and get the value. Throw an
     * IO exception if something goes wrong.
     * 
     * @param <T>    Return type of the future.
     * @param future The future to get the value of.
     * @return The completed value.
     * @throws IOException      If the future execution fails or times out.
     * @throws RuntimeException If the thread is interrupted.
     */
    public static <T> T getOrThrow(Future<T> future) throws IOException, RuntimeException {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException(e);
        }
    }
    
    /**
     * Return the exceptional value of a future if completed. If the future is not
     * completed or it didn't complete exceptionally, return an empty optional.
     * 
     * @param future The future to use.
     * @return The thrown exception, if any.
     */
    public static Optional<Throwable> getException(CompletableFuture<?> future) {
        try {
            future.getNow(null);
        } catch (CompletionException e) {
            return Optional.of(e.getCause());
        }
        return Optional.empty();
    }
}
