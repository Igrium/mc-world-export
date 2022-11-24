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
    public static Builder<RecursiveFloodFill> recursive = new Builder<>(RecursiveFloodFill::new);

    protected final Predicate<BlockPos> predicate;
    protected final Consumer<BlockPos> function;
    protected final boolean edges;
    protected final boolean corners;

    public FloodFill(Predicate<BlockPos> predicate, Consumer<BlockPos> function, boolean edges, boolean corners) {
        this.predicate = predicate;
        this.function = function;
        this.edges = edges;
        this.corners = corners;
    }

    public abstract void execute(BlockPos start);

    public static class Builder<T extends FloodFill> {
        public static interface Factory<T extends FloodFill> {
            T create(Predicate<BlockPos> predicate, Consumer<BlockPos> function, boolean edges, boolean corners);
        }

        private final Factory<T> factory;

        Predicate<BlockPos> predicate = pos -> false;
        Consumer<BlockPos> function = pos -> {};
        boolean edges = false;
        boolean corners = false;
        
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

        public Builder<T> edges(boolean edges) {
            this.edges = edges;
            return this;
        }

        public Builder<T> corners(boolean corners) {
            this.corners = corners;
            return this;
        }

        public T build() {
            return factory.create(predicate, function, edges, corners);
        }
    }

    // https://en.wikipedia.org/wiki/Flood_fill#Stack-based_recursive_implementation_(four-way)
    public static class RecursiveFloodFill extends FloodFill {

        public RecursiveFloodFill(Predicate<BlockPos> predicate, Consumer<BlockPos> function, boolean edges, boolean corners) {
            super(predicate, function, edges, corners);
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
