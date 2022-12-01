package org.scaffoldeditor.worldexport.util;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.util.math.BlockPos;

/**
 * An algorithm for executing a <a href="https://en.wikipedia.org/wiki/Flood_fill">flood fill</a>
 */
public abstract class FloodFill {
    public static Builder<RecursiveFloodFill> recursive() {
        return new Builder<>(RecursiveFloodFill::new);
    }

    protected final Predicate<BlockPos> predicate;
    protected final Consumer<BlockPos> function;
    protected final boolean edges;
    protected final boolean corners;
    protected final int maxDepth;

    public FloodFill(Predicate<BlockPos> predicate, Consumer<BlockPos> function, int maxDepth, boolean edges, boolean corners) {
        this.predicate = predicate;
        this.function = function;
        this.edges = edges;
        this.corners = corners;
        this.maxDepth = maxDepth;
    }

    public abstract void execute(BlockPos start);

    public static class Builder<T extends FloodFill> {
        public static interface Factory<T extends FloodFill> {
            T create(Predicate<BlockPos> predicate, Consumer<BlockPos> function, int maxDepth, boolean edges, boolean corners);
        }

        private final Factory<T> factory;

        Predicate<BlockPos> predicate = pos -> false;
        Consumer<BlockPos> function = pos -> {};
        int maxDepth = Integer.MAX_VALUE;
        boolean edges = false;
        boolean corners = false;

        public Builder<T> copy() {
            Builder<T> builder = new Builder<>(factory);
            builder.predicate = predicate;
            builder.function = function;
            builder.maxDepth = maxDepth;
            builder.edges = edges;
            builder.corners = corners;
            return builder;
        }
        
        public Builder(Factory<T> factory) {
            this.factory = factory;
        }

        public Builder<T> predicate(Predicate<BlockPos> predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder<T> function(Consumer<BlockPos> function) {
            this.function = function;
            return this;
        }

        public Builder<T> maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder<T> edges(boolean edges) {
            this.edges = edges;
            return this;
        }

        public Builder<T> corners(boolean corners) {
            this.corners = corners;
            return this;
        }

        public T build() {
            return factory.create(predicate, function, maxDepth, edges, corners);
        }
    }

    // https://en.wikipedia.org/wiki/Flood_fill#Stack-based_recursive_implementation_(four-way)
    public static class RecursiveFloodFill extends FloodFill {

        public RecursiveFloodFill(Predicate<BlockPos> predicate, Consumer<BlockPos> function, int maxDepth, boolean edges, boolean corners) {
            super(predicate, function, maxDepth, edges, corners);
        }

        @Override
        public void execute(BlockPos start) {
            Set<BlockPos> blacklist = new HashSet<>();
            Queue<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);

            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                if (blacklist.contains(pos)) continue;
                if (predicate.test(pos)) {
                    function.accept(pos);
                    blacklist.add(pos);

                    if (queue.size() >= maxDepth) continue;

                    queue.add(pos.add(1, 0, 0));
                    queue.add(pos.add(-1, 0, 0));
                    queue.add(pos.add(0, 1, 0));
                    queue.add(pos.add(0, -1, 0));
                    queue.add(pos.add(0, 0, 1));
                    queue.add(pos.add(0, 0, -1));

                    if (edges) {
                        queue.add(pos.add(-1, 1, 0));
                        queue.add(pos.add(1, 1, 0));
                        queue.add(pos.add(-1, -1, 0));
                        queue.add(pos.add(1, -1, 0));
                        
                        queue.add(pos.add(0, 1, -1));
                        queue.add(pos.add(0, -1, -1));
                        queue.add(pos.add(0, -1, 1));
                        queue.add(pos.add(0, 1, 1));

                        queue.add(pos.add(-1, 0, -1));
                        queue.add(pos.add(1, 0, -1));
                        queue.add(pos.add(1, 0, 1));
                        queue.add(pos.add(-1, 0, 1));
                    }

                    if (corners) {
                        queue.add(pos.add(-1, -1, -1));
                        queue.add(pos.add(1, -1, -1));
                        queue.add(pos.add(1, -1, 1));
                        queue.add(pos.add(-1, -1, 1));

                        queue.add(pos.add(-1, 1, -1));
                        queue.add(pos.add(1, 1, -1));
                        queue.add(pos.add(1, 1, 1));
                        queue.add(pos.add(-1, 1, 1));
                    }
                } else {
                    blacklist.add(pos);
                }
            }
        }

    }
}
