package com.igrium.worldexport.blockentity.model_adapters;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.blockentity.ModelBlockModelAdapter;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.mixin.AccessorSkullBlockRenderer;
import com.igrium.worldexport.replay.MaterialHolder;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public class SkullBlockModelAdapter extends ModelBlockModelAdapter<SkullBlockEntity, SkullBlockRenderState, SkullModelBase> {

    /**
     * Attempt to create a skull model adapter from an arbitrary block entity renderer.
     *
     * @param renderer The renderer to adapt.
     * @return The created model adapter.
     */
    public static SkullBlockModelAdapter fromRenderer(BlockEntityRenderer<?, ?> renderer) {
        return new SkullBlockModelAdapter((SkullBlockRenderer) renderer);
    }
    
    private @Nullable Identifier currentTexture;

    public SkullBlockModelAdapter(BlockEntityRenderer<? super SkullBlockEntity, SkullBlockRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void capture(SkullBlockEntity blockEntity, SkullBlockRenderState state, CapturedEntity capture,
                         MaterialHolder materials, Vec3 offset, int tick) {
        currentTexture = resolveTexture(blockEntity, state);
        super.capture(blockEntity, state, capture, materials, offset, tick);
    }

    private Identifier resolveTexture(SkullBlockEntity blockEntity, SkullBlockRenderState state) {
        // TODO: belay this until we have the texture loaded (like players)
        if (state.skullType == SkullBlock.Types.PLAYER) {
            ResolvableProfile profile = blockEntity.getOwnerProfile();
            if (profile != null) {
                var renderCache = ((AccessorSkullBlockRenderer) getRenderer()).getPlayerSkinRenderCache();
                return renderCache.getOrDefault(profile).playerSkin().body().texturePath();
            }
        }
        return AccessorSkullBlockRenderer.getSkinByType().get(state.skullType);
    }

    @Override
    protected void setupTransforms(SkullBlockEntity blockEntity, SkullBlockRenderState state, CapturedEntity capture,
                                    Vec3 offset, int tick) {
        Matrix4fc transform = state.transformation.getMatrix();

        Vector3f pos = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).add(offset).toVector3f()
                .add(transform.getTranslation(new Vector3f()));

        Quaternionf rot = transform.getNormalizedRotation(new Quaternionf());

        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS_ROT, pos, rot, null);
    }

    @Override
    protected SkullModelBase getModel(SkullBlockRenderState state) {
        return ((AccessorSkullBlockRenderer) getRenderer()).getModelByType().apply(state.skullType);
    }

    @Override
    protected void setupAnim(SkullModelBase model, SkullBlockRenderState state) {
        var modelState = new SkullModelBase.State();
        modelState.animationPos = state.animationProgress;
        model.setupAnim(modelState);
    }

    @Override
    protected Identifier getTexture(SkullBlockRenderState state) {
        return currentTexture;
    }
}
