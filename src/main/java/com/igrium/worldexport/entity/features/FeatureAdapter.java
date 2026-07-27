package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.mixin.AccessorRenderLayer;
import com.igrium.worldexport.replay.MaterialHolder;
import lombok.Getter;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public abstract class FeatureAdapter<S extends EntityRenderState, M extends EntityModel<? super S>> {

    public interface Factory<S extends EntityRenderState, T extends FeatureAdapter<S, ?>> {
        T create(RenderLayer<S, ?> renderer);
    }

    @Getter
    private final RenderLayer<S, M> renderer;

    public FeatureAdapter(RenderLayer<S, M> renderer) {
        this.renderer = renderer;
    }

    public RenderLayerParent<S, M> getContext() {
        return renderAccessor(renderer).getRenderer();
    }

    public M getContextModel() {
        return getContext().getModel();
    }

    public abstract void capture(CapturedEntity capture, MaterialHolder materials, S state, float limbAngle, float limbDistance, int tick);

    /**
     * Utility method to cast to AccessorFeatureRenderer while maintaining generics.
     */
    @SuppressWarnings("unchecked")
    public static <S extends EntityRenderState, M extends EntityModel<? super S>> AccessorRenderLayer<S, M> renderAccessor(
            RenderLayer<S, M> renderer) {
        return (AccessorRenderLayer<S, M>) renderer;
    }
}
