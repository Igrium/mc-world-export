package com.igrium.worldexport.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An executor wrapper where only a limited amount of concurrent executions can be applied at once.
 */
public class LimitedConcurrencyExecutor implements Executor {

    private final int maxConcurrent;
    private final Executor base;

    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger running = new AtomicInteger();

    public LimitedConcurrencyExecutor(int maxConcurrent, Executor base) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("maxConcurrent must be at least 1");
        }

        this.maxConcurrent = maxConcurrent;
        this.base = base;
    }

    @Override
    public void execute(@NotNull Runnable command) {
        if (incrementIfBelowMax(running, maxConcurrent)) {
            base.execute(wrap(command));
        } else {
            queue.add(command);
        }
    }

    private Runnable wrap(Runnable command) {
        return () -> {
            try {
                command.run();
            } finally {
                onFinish();
            }
        };
    }

    private void onFinish() {
        int num = running.decrementAndGet();
        assert num >= 0 : "Running tasks should not be less than zero. (" + num + ")";
        Runnable next = queue.poll();
        if (next != null) {
            base.execute(wrap(next));
        }
    }

    /**
     * Attempt to increment an atomic integer, as long as it is less than max.
     * @return If we were able to increment.
     */
    private static boolean incrementIfBelowMax(AtomicInteger value, int max) {
        while (true) {
            int current = value.get();
            if (current >= max) {
                return false;
            }
            int next = current + 1;
            if (value.compareAndSet(current, next)) {
                return true;
            }
        }
    }

}
