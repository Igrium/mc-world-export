package com.igrium.worldexport.util;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * <p>Executes a batch of keyed tasks across a fixed pool of threads, collecting each task's result.
 * <p>All tasks are queued up front and picked up by internal worker threads as they become available.
 * If it later becomes known that a task's result is no longer needed, it can be removed from the queue
 * (or its result discarded, if it has already run) using {@link #cancelTask}.
 *
 * @param <K> A unique task identifier
 * @param <I> A task's input type
 * @param <O> A task's output type
 */
public class TaskExecutor<K, I, O> {
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/TaskExecutor");

    /**
     * All tasks to be called (including canceled ones)
     */
    @Getter
    private final Map<K, I> tasks;
    private final int numThreads;

    private final BiFunction<? super K, ? super I, ? extends O> function;

    private final ConcurrentMap<K, I> queue = new ConcurrentHashMap<>();

    /**
     * If the optional is empty, the section was canceled. Kept in results as race-condition repellent
     */
    private final ConcurrentMap<K, Optional<O>> results = new ConcurrentHashMap<>();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicInteger activeThreads = new AtomicInteger(0);

    /**
     * A future that will complete once all tasks have completed.
     * If any task throws an exception, subsequent tasks are discarded and the future completes exceptionally.
     */
    @Getter
    private final CompletableFuture<Map<K, O>> completionFuture = new CompletableFuture<>();

    @Getter
    private volatile boolean aborted;

    @Getter
    private volatile Exception error;

    public boolean isFinished() {
        return completionFuture.isDone();
    }


    public TaskExecutor(Map<? extends K, ? extends I> tasks, int numThreads,
                        BiFunction<? super K, ? super I, ? extends O> function) {
        if (numThreads <= 0) {
            throw new IllegalArgumentException("Number of threads must be greater than zero.");
        }
        this.tasks = Map.copyOf(tasks);
        this.numThreads = numThreads;
        this.function = function;
    }

    /**
     * Stop submitting new tasks. Tasks already in progress will finish.
     */
    public void abort() {
        aborted = true;
    }

    /**
     * Cancel a task from processing. If it is already processing or has finished, discard the result.
     * @param key Task key
     */
    public void cancelTask(K key) {
        queue.remove(key);
        results.put(key, Optional.empty());
    }

    /**
     * Start the threads and begin executing the tasks.
     * @throws IllegalStateException If we've already begun execution.
     */
    public void start() throws IllegalStateException {
        if (started.getAndSet(true)) {
            throw new IllegalStateException("Task execution already started");
        }

        tasks.forEach((key, task) -> {
            // If it was already canceled, it would end up in results.
            if (!results.containsKey(key)) {
                queue.put(key, task);
            }
        });
        activeThreads.set(numThreads);

        for (int i = 0; i < numThreads; i++) {
            Thread thread = new Thread(this::runThread, "TaskExecutor " + i);
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void runThread() {
        while (!aborted) {
            var entry = removeAnyAtomic(queue);
            // We're out of tasks; close the thread
            if (entry == null) break;
            try {
                var result = function.apply(entry.getKey(), entry.getValue());
                //noinspection OptionalAssignedToNull
                if (results.putIfAbsent(entry.getKey(), Optional.of(result)) != null) {
                    LOGGER.warn("Rejected task result for {}", entry.getKey());
                }
            } catch (Exception e) {
                error = e;
                aborted = true;
                LOGGER.warn("Error computing {}", entry.getKey(), e);
            }
        }

        if (activeThreads.decrementAndGet() <= 0) {
            if (error != null) {
                completionFuture.completeExceptionally(error);
            } else {
                completionFuture.complete(results.entrySet().stream()
                        .filter(e -> e.getValue().isPresent())
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get())));
            }
        }
    }

    /**
     * Atomically remove and return any value from a concurrent map
     *
     * @param map Map to use
     * @return The removed entry. <code>null</code> if the map was empty
     */
    private static <K, V> @Nullable Map.Entry<K, V> removeAnyAtomic(ConcurrentMap<K, V> map) {
        for (var entry : map.entrySet()) {
            K key = entry.getKey();
            V value = map.remove(key);
            if (value != null) {
                return Map.entry(key, value);
            }
        }
        return null; // Map is empty
    }
}
