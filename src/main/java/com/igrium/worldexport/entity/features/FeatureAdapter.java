package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.mixin.AccessorFeatureRenderer;
import com.igrium.worldexport.replay.MaterialHolder;
import lombok.Getter;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;

public abstract class FeatureAdapter<S extends EntityRenderState, M extends EntityModel<? super S>> {

    public interface Factory<S extends EntityRenderState, T extends FeatureAdapter<S, ?>> {
        T create(FeatureRenderer<S, ?> renderer);
    }

    @Getter
    private final FeatureRenderer<S, M> renderer;

    public FeatureAdapter(FeatureRenderer<S, M> renderer) {
        this.renderer = renderer;
    }

    public FeatureRendererContext<S, M> getContext() {
        return renderAccessor(renderer).getContext();
    }

    public M getContextModel() {
        return getContext().getModel();
    }

    public abstract void capture(CapturedEntity capture, MaterialHolder materials, S state, float limbAngle, float limbDistance, int tick);

    /**
     * Utility method to cast to AccessorFeatureRenderer while maintaining generics.
     */
    @SuppressWarnings("unchecked")
    public static <S extends EntityRenderState, M extends EntityModel<? super S>> AccessorFeatureRenderer<S, M> renderAccessor(
            FeatureRenderer<S, M> renderer) {
        return (AccessorFeatureRenderer<S, M>) renderer;
    }
}
