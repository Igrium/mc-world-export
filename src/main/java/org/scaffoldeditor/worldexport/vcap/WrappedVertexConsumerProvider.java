package org.scaffoldeditor.worldexport.vcap;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

/**
 * A simple VertexConsumerProvider meant for wrapping custom vertex consumers.
 */
public class WrappedVertexConsumerProvider implements VertexConsumerProvider {

    private final Map<RenderLayer, VertexConsumer> existingBuffers = new HashMap<>();
    public final VertexConsumer base;

    protected final Set<RenderLayer> whitelist = new HashSet<>();
    protected final Set<RenderLayer> blacklist = new HashSet<>();

    public WrappedVertexConsumerProvider(VertexConsumer base) {
        this.base = base;
    }

    public WrappedVertexConsumerProvider(VertexConsumer base, @Nullable Collection<RenderLayer> whitelist, @Nullable Collection<RenderLayer> blacklist) {
        this.base = base;
        if (whitelist != null) this.whitelist.addAll(whitelist);
        if (blacklist != null) this.blacklist.addAll(blacklist);
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        if (isLayerAllowed(layer)) {
            return makeUnique(base, layer);
        } else {
            return makeUnique(EMPTY, layer);
        }
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
        if (existingBuffers.containsKey(layer)) {
            return existingBuffers.get(layer);
        } else {
            WrappedVertexConsumer wrapped = new WrappedVertexConsumer(consumer);
            existingBuffers.put(layer, wrapped);
            return wrapped;
        }
    }

    protected static class WrappedVertexConsumer implements VertexConsumer {
        public final VertexConsumer base;

        public WrappedVertexConsumer(VertexConsumer base) {
            this.base = base;
        }

        @Override
        public VertexConsumer vertex(double var1, double var3, double var5) {
            base.vertex(var1, var3, var5);
            return this;
        }

        @Override
        public VertexConsumer color(int var1, int var2, int var3, int var4) {
            base.color(var1, var2, var3, var4);
            return this;
        }

        @Override
        public VertexConsumer texture(float var1, float var2) {
            base.texture(var1, var2);
            return this;
        }

        @Override
        public VertexConsumer overlay(int var1, int var2) {
            base.overlay(var1, var2);
            return this;
        }

        @Override
        public VertexConsumer light(int var1, int var2) {
            base.light(var1, var2);
            return this;
        }

        @Override
        public VertexConsumer normal(float var1, float var2, float var3) {
            base.normal(var1, var2, var3);
            return this;
        }

        @Override
        public void next() {
            base.next();
        }

        @Override
        public void fixedColor(int var1, int var2, int var3, int var4) {
            base.fixedColor(var1, var2, var3, var4);
        }

        @Override
        public void unfixColor() {
            base.unfixColor();
        }
    }

    public static final VertexConsumer EMPTY = new VertexConsumer() {

        @Override
        public VertexConsumer vertex(double var1, double var3, double var5) {
            return this;
        }

        @Override
        public VertexConsumer color(int var1, int var2, int var3, int var4) {
            return this;
        }

        @Override
        public VertexConsumer texture(float var1, float var2) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int var1, int var2) {
            return this;
        }

        @Override
        public VertexConsumer light(int var1, int var2) {
            return this;
        }

        @Override
        public VertexConsumer normal(float var1, float var2, float var3) {
            return this;
        }

        @Override
        public void next() {}

        @Override
        public void fixedColor(int var1, int var2, int var3, int var4) {}

        @Override
        public void unfixColor() {}
        
    };
    
}
