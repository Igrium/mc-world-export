package com.igrium.worldexport.collectionutils;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntFunction;

/**
 * A thread-safe list wrapper where write operations are synchronized, but read operations are not.
 */
public class WriteSynchronizedList<T> implements List<T> {

    private static class RandomAccessSyncList<T> extends WriteSynchronizedList<T> implements RandomAccess {

        private RandomAccessSyncList(List<T> baseList) {
            super(baseList);
        }
    }

    /**
     * Create a write-synchronized wrapper.
     * @param baseList The backing list. The implementation must have thread-safe read operations.
     * @return The synchronized wrapper
     */
    public static <T> WriteSynchronizedList<T> of(List<T> baseList) {
        if (baseList instanceof RandomAccess) {
            return new RandomAccessSyncList<>(baseList);
        } else {
            return new WriteSynchronizedList<>(baseList);
        }
    }

    private final List<T> baseList;

    @Getter
    public final ReadWriteLock lock = new ReentrantReadWriteLock();

    private WriteSynchronizedList(List<T> baseList) {
        this.baseList = baseList;
    }

    public static <T> WriteSynchronizedList<T> wrap(List<T> list) {
        return new WriteSynchronizedList<>(list);
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return baseList.size();
        } finally {
            lock.readLock().unlock();
        }
    }


    @Override
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return baseList.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean contains(Object o) {
        lock.readLock().lock();
        try {
            return baseList.contains(o);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        // Iterator is not thread-safe. User must manually synchronize if needed.
        return baseList.iterator();
    }

    @Override
    public Object @NotNull [] toArray() {
        lock.readLock().lock();
        try {
            return baseList.toArray();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public <T1> T1 @NotNull [] toArray(@NotNull T1[] a) {
        lock.readLock().lock();
        try {
            return baseList.toArray(a);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public <T1> T1[] toArray(@NotNull IntFunction<T1[]> generator) {
        lock.readLock().lock();
        try {
            return baseList.toArray(generator);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean add(T t) {
        lock.writeLock().lock();
        try {
            return baseList.add(t);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        lock.writeLock().lock();
        try {
            return baseList.remove(o);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void sort(@Nullable Comparator<? super T> c) {
        lock.writeLock().lock();
        try {
            baseList.sort(c);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        lock.readLock().lock();
        try {
            return baseList.containsAll(c);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> c) {
        lock.writeLock().lock();
        try {
            return baseList.addAll(c);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends T> c) {
        lock.writeLock().lock();
        try {
            return baseList.addAll(index, c);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        lock.writeLock().lock();
        try {
            return baseList.removeAll(c);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        lock.writeLock().lock();
        try {
            return baseList.retainAll(c);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            baseList.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public T get(int index) {
        lock.readLock().lock();
        try {
            return baseList.get(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public T set(int index, T element) {
        lock.writeLock().lock();
        try {
            return baseList.set(index, element);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void add(int index, T element) {
        lock.writeLock().lock();
        try {
            baseList.add(index, element);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public T remove(int index) {
        lock.writeLock().lock();
        try {
            return baseList.remove(index);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int indexOf(Object o) {
        lock.readLock().lock();
        try {
            return baseList.indexOf(o);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int lastIndexOf(Object o) {
        lock.readLock().lock();
        try {
            return baseList.lastIndexOf(o);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull ListIterator<T> listIterator() {
        // ListIterator is not thread-safe. User must manually synchronize if needed.
        return baseList.listIterator();
    }

    @Override
    public @NotNull ListIterator<T> listIterator(int index) {
        // ListIterator is not thread-safe. User must manually synchronize if needed.
        return baseList.listIterator(index);
    }

    @Override
    public @NotNull List<T> subList(int fromIndex, int toIndex) {
        lock.readLock().lock();
        try {
            return baseList.subList(fromIndex, toIndex);
        } finally {
            lock.readLock().unlock();
        }
    }
}