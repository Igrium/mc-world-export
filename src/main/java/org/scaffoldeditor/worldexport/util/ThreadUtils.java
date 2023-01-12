package org.scaffoldeditor.worldexport.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import com.mojang.blaze3d.systems.RenderSystem;

public final class ThreadUtils {
    private ThreadUtils() {};

    /**
     * A supplier that can throw checked exceptions.
     */
    public static interface ThrowingSupplier<T, E extends Exception> {
        T get() throws E;
    }

    /**
     * A runnable that can throw checked exceptions.
     */
    public static interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }

    /**
     * Evaluate a value on the render thread. If we're already on the render thread,
     * evaluate it now.
     * 
     * @param <T> The type of value.
     * @param r   The supplier of the value.
     * @return A future that completes once the value has been evaluated.
     */
    public static <T> CompletableFuture<T> onRenderThread(Supplier<T> r) {
        if (RenderSystem.isOnRenderThread()) {
            try {
                return CompletableFuture.completedFuture(r.get());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        } else {
            CompletableFuture<T> future = new CompletableFuture<>();
            RenderSystem.recordRenderCall(() -> {
                try {
                    future.complete(r.get());
                } catch (Exception e) { // Let errors crash the game
                    future.completeExceptionally(e);
                }
            });
            return future;
        }
    }

    /**
     * Execute a runnable on the render thread. If we're already on the render
     * thread, execute it now.
     * 
     * @param r The runnable.
     * @return A future that completes once the runnable has been executed.
     */
    public static CompletableFuture<Void> onRenderThread(Runnable r) {
        return onRenderThread(() -> {
            r.run();
            return null;
        });
    }

    public static <T> CompletableFuture<T> supplyDangerous(ThrowingSupplier<T, ?> supplier, Executor executor) {
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

    public static CompletableFuture<Void> runDangerous(ThrowingRunnable<?> r, Executor executor) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                r.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
