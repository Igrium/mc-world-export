package com.igrium.worldexport.blockentity.model_adapters;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.blockentity.ModelBlockModelAdapter;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.mixin.AccessorShulkerBoxRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer.ShulkerBoxModel;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ShulkerBoxModelAdapter extends ModelBlockModelAdapter<ShulkerBoxBlockEntity, ShulkerBoxRenderState, ShulkerBoxModel> {

    /**
     * Attempt to create a shulker box model adapter from an arbitrary block entity renderer.
     *
     * @param renderer The renderer to adapt.
     * @return The created model adapter.
     */
    public static ShulkerBoxModelAdapter fromRenderer(BlockEntityRenderer<?, ?> renderer) {
        return new ShulkerBoxModelAdapter((ShulkerBoxRenderer) renderer);
    }

    public ShulkerBoxModelAdapter(BlockEntityRenderer<? super ShulkerBoxBlockEntity, ShulkerBoxRenderState> renderer) {
        super(renderer);
    }

    @Override
    protected ShulkerBoxModel getModel(ShulkerBoxRenderState state) {
        return ((AccessorShulkerBoxRenderer) getRenderer()).getModel();
    }

    @Override
    protected void setupTransforms(ShulkerBoxBlockEntity blockEntity, ShulkerBoxRenderState state,
                                    CapturedEntity capture, Vec3 offset, int tick) {
        Matrix4fc transform = ShulkerBoxRenderer.modelTransform(state.direction).getMatrix();

        Vector3f pos = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).add(offset).toVector3f()
                .add(transform.getTranslation(new Vector3f()));

        Quaternionf rot = transform.getNormalizedRotation(new Quaternionf());

        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS_ROT, pos, rot, null);
    }

    @Override
    protected void setupAnim(ShulkerBoxModel model, ShulkerBoxRenderState state) {
        model.setupAnim(state.progress);
    }

    @Override
    protected Identifier getTexture(ShulkerBoxRenderState state) {
        DyeColor color = state.color;
        SpriteId sprite = color == null ? Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION : Sheets.getShulkerBoxSprite(color);
        return SpriteSource.TEXTURE_ID_CONVERTER.idToFile(sprite.texture());
    }
}
