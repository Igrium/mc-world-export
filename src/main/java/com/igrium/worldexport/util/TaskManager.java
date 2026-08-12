package com.igrium.worldexport.util;


import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * <p>Executes a batch of keyed tasks across a fixed pool of threads, collecting each task's result.
 * <p>All tasks are queued up front and picked up by internal worker threads as they become available.
 * If it later becomes known that a task's result is no longer needed, it can be removed from the queue
 * (or its result discarded, if it has already run) using {@link #cancelTask}.
 *
 * @param <K> A unique task identifier
 * @param <P> A task's input type
 * @param <O> A task's output type
 */
public class TaskManager<K, P, O> {
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/TaskManager");;

    private final ConcurrentHashMap<K, P> queue = new ConcurrentHashMap<>();
    public Map<K, P> getQueue() {
        return Collections.unmodifiableMap(queue);
    }

    private final ConcurrentHashMap<K, Optional<O>> results = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<K, P> running = new ConcurrentHashMap<>();


    /**
     * Used during the shutdown sequence
     */
    private final AtomicInteger activeThreads = new AtomicInteger();

    private final int numThreads;
    private final List<Worker> workers;

    private final BiFunction<? super K, ? super P, ? extends O> function;

    private final AtomicBoolean started = new AtomicBoolean(false);

    @Getter
    private volatile boolean finishing;

    @Getter
    private volatile boolean aborted;

    private final AtomicReference<Exception> error = new AtomicReference<>();

    public @Nullable Exception getError() {
        return error.get();
    }

    /**
     * All the keys which were computed and subsequently discarded
     */
    @Getter
    private final Set<K> redundantExecutions = ConcurrentHashMap.newKeySet();

    /**
     * Get the number of tasks that have either been canceled or finished.
     */
    public int getFinishedTasks() {
        return results.size();
    }

    /**
     * Count the number of tasks that have been canceled.
     */
    public int getCanceledTasks() {
        int canceled = 0;
        for (var val : results.values()) {
            if (val.isEmpty()) canceled++;
        }
        return canceled;
    }

    /**
     * Count the number of tasks that have been fully completed (and not canceled)
     */
    public int getCompletedTasks() {
        int completed = 0;
        for (var val : results.values()) {
            if (val.isPresent()) completed++;
        }
        return completed;
    }

    /**
     * A future that will complete once all tasks have completed.
     * If any task throws an exception, subsequent tasks are discarded and the future completes exceptionally.
     */
    @Getter
    private final CompletableFuture<Map<K, O>> completionFuture = new CompletableFuture<>();

    public int getNumRunning() {
        return running.size();
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isFinished() {
        return completionFuture.isDone();
    }

    public TaskManager(int numThreads, BiFunction<? super K, ? super P, ? extends O> function) {
        if (numThreads <= 0) {
            throw new IllegalArgumentException("numThreads must be greater than zero");
        }
        this.function = function;
        workers = new ArrayList<>(numThreads);
        this.numThreads = numThreads;
    }

    /**
     * Stop starting new tasks. Tasks already in progress will finish.
     */
    public void abort() {
        aborted = true;
        unparkAll();
    }

    /**
     * Shut down this task manager gracefully; queued tasks will complete, but no more will be accepted.
     * @return The task results
     */
    public CompletableFuture<Map<K, O>> finish() {
        finishing = true;
        unparkAll();
        return completionFuture;
    }

    /**
     * Enqueue a task to be run
     *
     * @param key   Task key
     * @param param Task parameter
     * @return If the task was able to be queued (not already queued, running, complete, or canceled)
     */
    public boolean addTask(K key, P param) {
        if (aborted || finishing) {
            LOGGER.error("Task manager is in the processes of shutting down.");
            return false;
        }
        if (!results.containsKey(key) && !running.containsKey(key) && queue.putIfAbsent(key, param) == null) {
            unparkAll();
            return true;
        }
        return false;
    }

    public void addTasks(Map<? extends K, ? extends P> params) {
        for (var  entry : params.entrySet()) {
            addTask(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Cancel a task from processing. If it is already processing or has finished, discard the result.
     * @param key Task key
     */
    public void cancelTask(K key) {
        //noinspection OptionalAssignedToNull (null means opt wasn't in map)
        if (results.put(key, Optional.empty()) != null) {
            redundantExecutions.add(key);
        }
        queue.remove(key);
    }

    /**
     * Start the threads and begin executing the tasks.
     * @return <code>true</code> if we successfully started; <code>false</code> if we'd already started
     */
    public boolean start() {
        // Don't start twice
        if (started.getAndSet(true)) {
            return false;
        }

        // If it was already canceled, it would end up in results.
        queue.keySet().removeIf(results::containsKey);

        activeThreads.set(numThreads);
        for (int i = 0; i < numThreads; i++) {
            Worker thread = new Worker(i);
            thread.setDaemon(true);
            workers.add(thread);
            thread.start();
        }
        return true;
    }

    /**
     * Get a snapshot of all the results that have been completed so far
     */
    public Map<K, O> getResults() {
        return results.entrySet().stream()
                .filter(entry -> entry.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    /**
     * A snapshot of all the values that have been canceled.
     */
    public Set<K> getCancelled() {
        return results.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }


    private void unparkAll() {
        for (var w : workers) {
            LockSupport.unpark(w);
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


    private class Worker extends Thread {
        volatile boolean parked;

        Worker(int idx) {
            super("TaskManager-" + idx);
        }

        @Override
        public void run() {
            try {
                while (!aborted) {
                    var entry = removeAnyAtomic(queue);
                    if (entry != null) {
                        running.put(entry.getKey(), entry.getValue());
                        try {
                            var result = function.apply(entry.getKey(), entry.getValue());
                            //noinspection OptionalAssignedToNull (null and optional.empty mean different things here)
                            if (results.putIfAbsent(entry.getKey(), Optional.of(result)) != null) {
                                LOGGER.warn("Rejected task result for {}", entry.getKey());
                                redundantExecutions.add(entry.getKey());
                            }
                        } catch (Exception e) {
                            error.compareAndSet(null, e);
                            abort();
                            LOGGER.error("Error computing {}", entry.getKey(), e);
                        } finally {
                            running.remove(entry.getKey());
                        }
                    } else {
                        // Won't hit this line if there's still more tasks, differentiating stopping from aborted
                        if (finishing) {
                            break;
                        } else {
                            park();
                        }
                    }
                }
            } finally {
                // Shutdown sequence
                if (activeThreads.decrementAndGet() <= 0) {
                    var e = getError();
                    if (e != null) {
                        completionFuture.completeExceptionally(e);
                    } else {
                        completionFuture.complete(getResults());
                    }
                }
            }

        }

        private void park() {
            parked = true;
            LockSupport.park();
            parked = false;
        }
    }
}
