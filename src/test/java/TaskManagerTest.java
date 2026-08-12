import com.igrium.worldexport.util.TaskManager;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * vibe-coded unit tests. bite me.
 * <p>
 * Unlike the old TaskExecutor, TaskManager takes its tasks incrementally via
 * {@link TaskManager#addTask} / {@link TaskManager#addTasks} and keeps its workers
 * alive (parked) until {@link TaskManager#finish()} is called. So the shape of most
 * tests is: construct -> start -> add tasks -> stop -> await completionFuture.
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
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(8, (k, v) -> v * 2);
        manager.addTasks(makeTasks(50));
        manager.start();

        Map<Integer, Integer> result = manager.finish().get(5, TimeUnit.SECONDS);
        assertEquals(50, result.size());
        for (int i = 0; i < 50; i++) {
            assertEquals(i * 2, result.get(i));
        }
        assertTrue(manager.isFinished());
    }

    @Test
    @Timeout(10)
    void tasksAddedAfterStartAreRun() throws Exception {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> v * 2);
        manager.start();

        // Give the workers a moment to spin up and park on an empty queue.
        Thread.sleep(100);
        assertFalse(manager.isFinished(), "manager should not finish before stop()");

        manager.addTasks(makeTasks(100));

        Map<Integer, Integer> result = manager.finish().get(5, TimeUnit.SECONDS);
        assertEquals(100, result.size());
        for (int i = 0; i < 100; i++) {
            assertEquals(i * 2, result.get(i));
        }
    }

    @Test
    void tasksAddedOneAtATimeWhileRunning() throws Exception {
        int n = 500;
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> v + 1);
        manager.start();

        for (int i = 0; i < n; i++) {
            assertTrue(manager.addTask(i, i), "addTask should accept a fresh key: " + i);
        }

        Map<Integer, Integer> result = manager.finish().get(10, TimeUnit.SECONDS);
        assertEquals(n, result.size());
        for (int i = 0; i < n; i++) {
            assertEquals(i + 1, result.get(i));
        }
    }

    @Test
    @Timeout(20)
    void highConcurrencyLargeBatch() throws Exception {
        int n = 5000;
        AtomicInteger invocations = new AtomicInteger();
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(32, (k, v) -> {
            invocations.incrementAndGet();
            return v + 1;
        });
        manager.addTasks(makeTasks(n));
        manager.start();

        Map<Integer, Integer> result = manager.finish().get(15, TimeUnit.SECONDS);
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
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> v);
        manager.addTasks(makeTasks(10));
        manager.start();

        assertFalse(manager.start(), "double-start should return false");

        Map<Integer, Integer> result = manager.finish().get(5, TimeUnit.SECONDS);
        assertEquals(10, result.size());
    }

    @Test
    void invalidNumThreadsZero() {
        assertThrows(IllegalArgumentException.class, () -> new TaskManager<>(0, (k, v) -> v));
    }

    @Test
    void invalidNumThreadsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new TaskManager<>(-3, (k, v) -> v));
    }

    @Test
    @Timeout(10)
    void addTaskRejectsDuplicatesCancelledAndCompleted() throws Exception {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(2, (k, v) -> v);

        assertTrue(manager.addTask(1, 1), "first add of a key should succeed");
        assertFalse(manager.addTask(1, 99), "re-adding a queued key should be rejected");

        manager.cancelTask(2);
        assertFalse(manager.addTask(2, 2), "re-adding a canceled key should be rejected");

        manager.start();
        Map<Integer, Integer> result = manager.finish().get(5, TimeUnit.SECONDS);
        assertEquals(Map.of(1, 1), result);

        // Key 1 has a result now, so it can't be re-queued.
        assertFalse(manager.addTask(1, 1), "re-adding a completed key should be rejected");
    }

    @Test
    @Timeout(10)
    void isStartedReflectsState() throws Exception {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(2, (k, v) -> v);
        assertFalse(manager.isStarted());
        assertFalse(manager.isFinished());
        manager.start();
        assertTrue(manager.isStarted());

        manager.finish().get(5, TimeUnit.SECONDS);
        assertTrue(manager.isFinished());
        assertTrue(manager.isFinishing());
    }

    @Test
    @Timeout(10)
    void numRunningTracksInFlightTasks() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> {
            bothStarted.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return v;
        });
        manager.addTasks(makeTasks(2));
        manager.start();

        assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
        assertEquals(2, manager.getNumRunning(), "both tasks should be reported as running");

        release.countDown();
        manager.finish().get(5, TimeUnit.SECONDS);
        assertEquals(0, manager.getNumRunning(), "nothing should be running once finished");
    }

    @RepeatedTest(50)
    @Timeout(10)
    void cancelBeforeStart() throws Exception {
        AtomicInteger calledFor5 = new AtomicInteger();
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> {
            if (k == 5) calledFor5.incrementAndGet();
            return v;
        });
        manager.addTasks(makeTasks(10));
        manager.cancelTask(5);
        manager.start();

        Map<Integer, Integer> result = manager.finish().get(5, TimeUnit.SECONDS);
        assertEquals(9, result.size());
        assertFalse(result.containsKey(5));
        assertEquals(0, calledFor5.get());
        assertTrue(manager.getCancelled().contains(5));
    }

    @Test
    @Timeout(10)
    void cancelRunningOrFinishedTask() throws Exception {
        int n = 20;
        AtomicInteger invocations = new AtomicInteger();
        CountDownLatch startedLatch = new CountDownLatch(1);
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> {
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
        manager.addTasks(makeTasks(n));
        manager.start();
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS));
        manager.cancelTask(0);

        Map<Integer, Integer> result = manager.finish().get(5, TimeUnit.SECONDS);
        assertFalse(result.containsKey(0), "canceled task's result must be discarded");
        assertEquals(n - 1, result.size());
        // The function may still have run for key 0's side effects.
        assertTrue(invocations.get() >= n - 1);
    }

    @Test
    @Timeout(10)
    void cancelAfterCompletionHasNoEffect() throws Exception {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(2, (k, v) -> v);
        manager.addTasks(makeTasks(5));
        manager.start();

        Map<Integer, Integer> result = manager.finish().get(5, TimeUnit.SECONDS);
        assertEquals(5, result.size());

        manager.cancelTask(2);
        Map<Integer, Integer> resultAgain = manager.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertEquals(result, resultAgain);
        assertTrue(resultAgain.containsKey(2));
    }

    @Test
    @Timeout(10)
    void cancelUnknownKeyIsHarmless() throws Exception {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(2, (k, v) -> v);
        manager.addTasks(makeTasks(5));
        manager.cancelTask(999);
        manager.start();

        Map<Integer, Integer> result = manager.finish().get(5, TimeUnit.SECONDS);
        assertEquals(5, result.size());
        assertFalse(result.containsKey(999));
    }

    @Test
    @Timeout(10)
    void failingTaskFailsFutureAndSetsError() {
        RuntimeException boom = new RuntimeException("boom");
        AtomicInteger invocations = new AtomicInteger();
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> {
            invocations.incrementAndGet();
            if (k == 10) {
                throw boom;
            }
            return v;
        });
        manager.addTasks(makeTasks(20));
        manager.start();

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> manager.getCompletionFuture().get(5, TimeUnit.SECONDS));
        assertSame(boom, ex.getCause());
        assertTrue(manager.isAborted(), "a thrown task should abort the manager");
        assertSame(boom, manager.getError());

        // Bounded: shouldn't have run every remaining task after the failure.
        assertTrue(invocations.get() <= 20);
    }

    @Test
    @Timeout(10)
    void abortBeforeAnyTaskStarts() throws Exception {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> v);
        manager.addTasks(makeTasks(100));
        manager.abort();
        assertTrue(manager.isAborted());
        manager.start();

        // Abort means the workers wind down without needing stop().
        Map<Integer, Integer> result = manager.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertTrue(result.size() <= 100);
        for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue());
        }
    }

    @Test
    @Timeout(15)
    void abortMidRun() throws Exception {
        int n = 200;
        CountDownLatch someStarted = new CountDownLatch(5);
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(8, (k, v) -> {
            someStarted.countDown();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return v;
        });
        manager.addTasks(makeTasks(n));
        manager.start();
        assertTrue(someStarted.await(5, TimeUnit.SECONDS));
        manager.abort();

        Map<Integer, Integer> result = manager.getCompletionFuture().get(10, TimeUnit.SECONDS);
        assertTrue(result.size() < n, "abort should have skipped at least some queued tasks");
        assertFalse(manager.getCompletionFuture().isCompletedExceptionally());
        for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue());
        }
    }

    @Test
    @Timeout(5)
    void getQueueIsAnUnmodifiableView() {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(2, (k, v) -> v);
        manager.addTasks(makeTasks(5));

        Map<Integer, Integer> queue = manager.getQueue();
        assertEquals(makeTasks(5), queue);
        assertThrows(UnsupportedOperationException.class, () -> queue.put(1000, 1000));

        // Cancellation removes a task from the queue.
        manager.cancelTask(0);
        assertFalse(manager.getQueue().containsKey(0));
    }

    @Test
    @Timeout(10)
    void queueDrainsAsTasksComplete() throws Exception {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> v);
        manager.addTasks(makeTasks(50));
        assertEquals(50, manager.getQueue().size());
        manager.start();

        manager.finish().get(5, TimeUnit.SECONDS);
        assertTrue(manager.getQueue().isEmpty(), "queue should be empty once all tasks ran");
        assertEquals(50, manager.getResults().size());
    }

    @Test
    @Timeout(10)
    void noTasksResolvesEmpty() throws Exception {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> v);
        manager.start();

        Map<Integer, Integer> result = manager.finish().get(5, TimeUnit.SECONDS);
        assertTrue(result.isEmpty());
    }

    @Test
    @Timeout(10)
    void finishBeforeStartStillResolves() throws Exception {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(4, (k, v) -> v);
        manager.addTasks(makeTasks(10));
        manager.finish();
        manager.start();

        // Workers see `stopping` immediately, but must still drain what's queued.
        Map<Integer, Integer> result = manager.getCompletionFuture().get(5, TimeUnit.SECONDS);
        assertEquals(10, result.size());
    }

    @Test
    @Timeout(5)
    void neverStartedNeverResolves() {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(2, (k, v) -> v);
        manager.addTasks(makeTasks(3));

        assertThrows(TimeoutException.class,
                () -> manager.getCompletionFuture().get(500, TimeUnit.MILLISECONDS));
    }

    @Test
    @Timeout(5)
    void neverStoppedNeverResolves() {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(2, (k, v) -> v);
        manager.addTasks(makeTasks(3));
        manager.start();

        // All tasks will finish, but the manager stays open for more work until stop().
        assertThrows(TimeoutException.class,
                () -> manager.getCompletionFuture().get(500, TimeUnit.MILLISECONDS));
    }

    @Test
    @Timeout(15)
    void manyThreadsMoreThanTasksIsHarmless() throws Exception {
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(64, (k, v) -> v * 10);
        manager.addTasks(makeTasks(5));
        manager.start();

        Map<Integer, Integer> result = manager.finish().get(5, TimeUnit.SECONDS);
        assertEquals(5, result.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i * 10, result.get(i));
        }
    }

    @Test
    @Timeout(20)
    void concurrentCancellationDuringHighConcurrencyRun() throws Exception {
        int n = 2000;
        Set<Integer> canceledKeys = ConcurrentHashMap.newKeySet();

        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(16, (k, v) -> v);
        manager.addTasks(makeTasks(n));

        Thread canceller = new Thread(() -> {
            for (int i = 0; i < n; i += 3) {
                manager.cancelTask(i);
                canceledKeys.add(i);
            }
        });

        manager.start();
        canceller.start();
        canceller.join(10000);

        Map<Integer, Integer> result = manager.finish().get(15, TimeUnit.SECONDS);

        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
            assertTrue(seen.add(entry.getKey()));
            assertEquals(entry.getKey(), entry.getValue());
            assertTrue(entry.getKey() >= 0 && entry.getKey() < n);
            assertFalse(canceledKeys.contains(entry.getKey()),
                    "canceled key must never appear in the results: " + entry.getKey());
        }
        assertTrue(result.size() <= n - canceledKeys.size());
    }

    @Test
    @Timeout(20)
    void concurrentProducerWhileRunning() throws Exception {
        int n = 2000;
        TaskManager<Integer, Integer, Integer> manager = new TaskManager<>(8, (k, v) -> v * 2);
        manager.start();

        Thread producer = new Thread(() -> {
            for (int i = 0; i < n; i++) {
                manager.addTask(i, i);
            }
        });
        producer.start();
        producer.join(10000);

        Map<Integer, Integer> result = manager.finish().get(15, TimeUnit.SECONDS);
        assertEquals(n, result.size());
        for (int i = 0; i < n; i++) {
            assertEquals(i * 2, result.get(i));
        }
    }
}
