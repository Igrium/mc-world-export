package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.mixin.AccessorRenderLayer;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.NativeImageReplayTexture;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.TextureExtractor;
import de.javagl.obj.Mtls;
import lombok.Getter;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

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
     * Register equipment texture and get or create its material.
     *
     * @param materials Material holder to write into.
     * @param texId     Equipment texture to register.
     * @return The material.
     */
    public static ReplayMtl getEquipmentMaterial(MaterialHolder materials, Identifier texId) {
        String texName = texId.getNamespace() + "/" + texId.getPath();
        String texPath = texName.endsWith(".png") ? texName : texName + ".png";

        materials.getTextures().computeIfAbsent(texPath, tex ->
                TextureExtractor.pullTextureAsync(texId).thenApply(NativeImageReplayTexture::new));

        return materials.getOrCreateMtl("entities.mtl", texName, n -> {
            ReplayMtl mtl = new ReplayMtl(Mtls.create(n));
            mtl.mtl().setMapKd(texPath);
            mtl.mtl().setMapD(texPath);
            mtl.properties().put("armor", ReplayMtl.Property.of(true));
            return mtl;
        });
    }

    /**
     * Utility method to cast to AccessorFeatureRenderer while maintaining generics.
     */
    @SuppressWarnings("unchecked")
    public static <S extends EntityRenderState, M extends EntityModel<? super S>> AccessorRenderLayer<S, M> renderAccessor(
            RenderLayer<S, M> renderer) {
        return (AccessorRenderLayer<S, M>) renderer;
    }
}
