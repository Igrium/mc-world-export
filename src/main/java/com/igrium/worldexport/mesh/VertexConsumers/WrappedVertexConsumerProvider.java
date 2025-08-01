package com.igrium.worldexport.mesh.VertexConsumers;

import lombok.Getter;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A simple VertexConsumerProvider meant for wrapping custom vertex consumers.
 */
public class WrappedVertexConsumerProvider implements VertexConsumerProvider {

    private final Map<RenderLayer, VertexConsumer> existingBuffers = new HashMap<>();


    public static final VertexConsumer EMPTY = new EmptyVertexConsumer();

    @Getter
    private final Set<RenderLayer> whitelist = new HashSet<>();

    @Getter
    private final Set<RenderLayer> blacklist = new HashSet<>();

    @Getter
    private final VertexConsumer base;

    public WrappedVertexConsumerProvider(VertexConsumer base) {
        this.base = base;
    }

    public WrappedVertexConsumerProvider(VertexConsumer base,
                                         @Nullable Collection<? extends RenderLayer> whitelist,
                                         @Nullable Collection<? extends RenderLayer> blacklist) {
        this.base = base;
        if (whitelist != null) this.whitelist.addAll(whitelist);
        if (blacklist != null) this.blacklist.addAll(blacklist);
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return isLayerAllowed(layer) ? makeUnique(base, layer) : makeUnique(EMPTY, layer);
    }

    protected boolean isLayerAllowed(RenderLayer layer) {
        if (!whitelist.isEmpty()) {
            return whitelist.contains(layer);
        } else if (!blacklist.isEmpty()) {
            return !blacklist.contains(layer);
        } else {
            return true;
        }
    }

    /**
     * For some reason, <code>VertexConsumers.union()</code> throws if both params
     * are equal rather than simply returning the vertex consumer. This ensures
     * that, even if we pass the same base vertex consumer twice, it never passes an
     * equals (<code>==</code>) check.
     *
     * @param consumer The vertex consumer to use.
     * @param layer The render layer to assign to.
     * @return The unique vertex consumer.
     */
    protected VertexConsumer makeUnique(VertexConsumer consumer, RenderLayer layer) {
        return existingBuffers.computeIfAbsent(layer, l -> new ForwardingVertexConsumer(consumer));

    }

    private static class EmptyVertexConsumer implements VertexConsumer {

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }
    }
}
