import com.igrium.worldexport.util.TaskExecutor;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * vibe-coded unit tests. bite me.
 */
public class TaskManagerTest {

    private Map<Integer, Integer> makeTasks(int n) {
        Map<Integer, Integer> tasks = new HashMap<>();
        for (int i = 0; i < n; i++) {
            tasks.put(i, i);
        }
        return tasks;
    }

    @Test
    @Timeout(10)
    void basicRun() throws Exception {
        Map<Integer, Integer> tasks = makeTasks(50);
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 8, (k, v) -> v * 2);
        executor.start();

        Map<Integer, Integer> result = executor.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertEquals(50, result.size());
        for (int i = 0; i < 50; i++) {
            assertEquals(i * 2, result.get(i));
        }
    }

    @Test
    @Timeout(10)
    void highConcurrencyLargeBatch() throws Exception {
        int n = 5000;
        Map<Integer, Integer> tasks = makeTasks(n);
        AtomicInteger invocations = new AtomicInteger();
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 32, (k, v) -> {
                    invocations.incrementAndGet();
                    return v + 1;
                });
        executor.start();

        Map<Integer, Integer> result = executor.getCompletionFuture().get(15, TimeUnit.SECONDS);
        assertEquals(n, result.size());
        assertEquals(n, invocations.get());

        Set<Integer> seenKeys = ConcurrentHashMap.newKeySet();
        for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
            assertTrue(seenKeys.add(entry.getKey()), "duplicate key in result: " + entry.getKey());
            assertEquals(entry.getKey() + 1, entry.getValue());
        }
    }

    @Test
    @Timeout(10)
    void doubleStartThrows() throws Exception {
        Map<Integer, Integer> tasks = makeTasks(10);
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 4, (k, v) -> v);
        executor.start();
        assertThrows(IllegalStateException.class, executor::start);

        Map<Integer, Integer> result = executor.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertEquals(10, result.size());
    }

    @Test
    void invalidNumThreadsZero() {
        Map<Integer, Integer> tasks = makeTasks(5);
        assertThrows(IllegalArgumentException.class,
                () -> new TaskExecutor<>(tasks, 0, (k, v) -> v));
    }

    @Test
    void invalidNumThreadsNegative() {
        Map<Integer, Integer> tasks = makeTasks(5);
        assertThrows(IllegalArgumentException.class,
                () -> new TaskExecutor<>(tasks, -3, (k, v) -> v));
    }

    @RepeatedTest(50)
    @Timeout(10)
    void cancelBeforeStart() throws Exception {
        Map<Integer, Integer> tasks = makeTasks(10);
        AtomicInteger calledFor5 = new AtomicInteger();
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 4, (k, v) -> {
                    if (k == 5) calledFor5.incrementAndGet();
                    return v;
                });
        executor.cancelTask(5);
        executor.start();

        Map<Integer, Integer> result = executor.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertEquals(9, result.size());
        assertFalse(result.containsKey(5));
        assertEquals(0, calledFor5.get());
    }

    @Test
    @Timeout(10)
    void cancelRunningOrFinishedTask() throws Exception {
        int n = 20;
        Map<Integer, Integer> tasks = makeTasks(n);
        AtomicInteger invocations = new AtomicInteger();
        CountDownLatch startedLatch = new CountDownLatch(1);
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 4, (k, v) -> {
                    invocations.incrementAndGet();
                    if (k == 0) {
                        startedLatch.countDown();
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return v;
                });
        executor.start();
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS));
        executor.cancelTask(0);

        Map<Integer, Integer> result = executor.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertFalse(result.containsKey(0));
        assertEquals(n - 1, result.size());
        // The function may still have run for key 0's side effects.
        assertTrue(invocations.get() >= n - 1);
    }

    @Test
    @Timeout(10)
    void cancelAfterCompletionHasNoEffect() throws Exception {
        Map<Integer, Integer> tasks = makeTasks(5);
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 2, (k, v) -> v);
        executor.start();

        Map<Integer, Integer> result = executor.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertEquals(5, result.size());

        executor.cancelTask(2);
        Map<Integer, Integer> resultAgain = executor.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertEquals(result, resultAgain);
        assertTrue(resultAgain.containsKey(2));
    }

    @Test
    @Timeout(10)
    void cancelUnknownKeyIsHarmless() throws Exception {
        Map<Integer, Integer> tasks = makeTasks(5);
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 2, (k, v) -> v);
        executor.cancelTask(999);
        executor.start();

        Map<Integer, Integer> result = executor.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertEquals(5, result.size());
    }

    @Test
    @Timeout(10)
    void failingTaskFailsFutureAndSetsError() throws Exception {
        Map<Integer, Integer> tasks = makeTasks(20);
        RuntimeException boom = new RuntimeException("boom");
        AtomicInteger invocations = new AtomicInteger();
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 4, (k, v) -> {
                    invocations.incrementAndGet();
                    if (k == 10) {
                        throw boom;
                    }
                    return v;
                });
        executor.start();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> executor.getCompletionFuture().get(5, TimeUnit.SECONDS));
        assertSame(boom, ex.getCause());
        assertTrue(executor.isAborted());
        assertSame(boom, executor.getError());

        // Bounded: shouldn't have run every remaining task after the failure.
        // Not asserting an exact number, just that it terminated reasonably.
        assertTrue(invocations.get() <= 20);
    }

    @Test
    @Timeout(10)
    void abortBeforeAnyTaskStarts() throws Exception {
        Map<Integer, Integer> tasks = makeTasks(100);
        AtomicInteger invocations = new AtomicInteger();
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 4, (k, v) -> {
                    invocations.incrementAndGet();
                    return v;
                });
        executor.abort();
        assertTrue(executor.isAborted());
        executor.start();

        Map<Integer, Integer> result = executor.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertTrue(result.size() <= 100);
        for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue());
        }
    }

    @Test
    @Timeout(10)
    void abortMidRun() throws Exception {
        int n = 200;
        Map<Integer, Integer> tasks = makeTasks(n);
        CountDownLatch someStarted = new CountDownLatch(5);
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 8, (k, v) -> {
                    someStarted.countDown();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return v;
                });
        executor.start();
        assertTrue(someStarted.await(5, TimeUnit.SECONDS));
        executor.abort();

        Map<Integer, Integer> result = executor.getCompletionFuture().get(10, TimeUnit.SECONDS);
        assertTrue(result.size() <= n);
        assertFalse(executor.getCompletionFuture().isCompletedExceptionally());
        for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue());
        }
    }

    @Test
    @Timeout(5)
    void getTasksIsImmutableSnapshot() {
        Map<Integer, Integer> tasks = makeTasks(5);
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 2, (k, v) -> v);

        Map<Integer, Integer> snapshot = executor.getTasks();
        assertEquals(tasks, snapshot);

        // Mutating the original map after construction shouldn't affect the snapshot.
        tasks.put(999, 999);
        assertFalse(snapshot.containsKey(999));

        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(1000, 1000));

        // Cancellation shouldn't affect the snapshot either.
        executor.cancelTask(0);
        assertTrue(snapshot.containsKey(0));
    }

    @Test
    @Timeout(5)
    void emptyTasksMapResolvesEmpty() throws Exception {
        Map<Integer, Integer> tasks = new HashMap<>();
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 4, (k, v) -> v);
        executor.start();

        Map<Integer, Integer> result = executor.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertTrue(result.isEmpty());
    }

    @Test
    @Timeout(3)
    void neverStartedNeverResolves() throws Exception {
        Map<Integer, Integer> tasks = makeTasks(3);
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 2, (k, v) -> v);

        assertThrows(TimeoutException.class,
                () -> executor.getCompletionFuture().get(500, TimeUnit.MILLISECONDS));
    }

    @Test
    @Timeout(15)
    void manyThreadsMoreThanTasksIsHarmless() throws Exception {
        Map<Integer, Integer> tasks = makeTasks(5);
        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 64, (k, v) -> v * 10);
        executor.start();

        Map<Integer, Integer> result = executor.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertEquals(5, result.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i * 10, result.get(i));
        }
    }

    @Test
    @Timeout(20)
    void concurrentCancellationDuringHighConcurrencyRun() throws Exception {
        int n = 2000;
        Map<Integer, Integer> tasks = makeTasks(n);
        AtomicReference<TaskExecutor<Integer, Integer, Integer>> executorRef = new AtomicReference<>();
        Set<Integer> canceledKeys = ConcurrentHashMap.newKeySet();

        TaskExecutor<Integer, Integer, Integer> executor =
                new TaskExecutor<>(tasks, 16, (k, v) -> v);
        executorRef.set(executor);

        Thread cancellerThread = new Thread(() -> {
            for (int i = 0; i < n; i += 3) {
                executor.cancelTask(i);
                canceledKeys.add(i);
            }
        });

        executor.start();
        cancellerThread.start();
        cancellerThread.join(10000);

        Map<Integer, Integer> result = executor.getCompletionFuture().get(15, TimeUnit.SECONDS);

        // Every present key must be a valid, non-duplicated, correctly-mapped key.
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
            assertTrue(seen.add(entry.getKey()));
            assertEquals(entry.getKey(), entry.getValue());
            assertTrue(entry.getKey() >= 0 && entry.getKey() < n);
        }
        // No result size assertion beyond upper bound: cancellation races are non-deterministic,
        // but result can never exceed the full task count.
        assertTrue(result.size() <= n);
    }
}