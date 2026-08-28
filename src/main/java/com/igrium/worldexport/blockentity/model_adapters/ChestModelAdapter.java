package com.igrium.worldexport.blockentity.model_adapters;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.blockentity.ModelBlockModelAdapter;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.ModelParts;
import com.igrium.worldexport.mixin.AccessorChestRenderer;
import com.igrium.worldexport.replay.MaterialHolder;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;


public class ChestModelAdapter<T extends BlockEntity & LidBlockEntity>
        extends ModelBlockModelAdapter<T, ChestRenderState, ChestModel> {

    /**
     * Attempt to create a chest model adapter from an arbitrary block entity renderer.
     * This method exists to deal with all the bullshit surrounding generics in regard to renderers.
     *
     * @param renderer The renderer to adapt.
     * @param <T>      Block entity type.
     * @return The created model adapter.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends BlockEntity> ChestModelAdapter fromRenderer(BlockEntityRenderer<? super T, ?> renderer) {
        // Yeah, this is probably unsafe, but I'm so tired of this generic bullshit that whatever.
        return new ChestModelAdapter((BlockEntityRenderer) renderer);
    }

    public ChestModelAdapter(BlockEntityRenderer<? super T, ChestRenderState> renderer) {
        super(renderer);
    }


    private static String variantRoot(ChestType type) {
        return "root/" + type.getSerializedName();
    }

    @Override
    protected ChestModel getModel(ChestRenderState state) {
        var models = ((AccessorChestRenderer) getRenderer()).getModels();
        return models.select(state.type);
    }

    @Override
    public void capture(T blockEntity, ChestRenderState state, CapturedEntity capture, MaterialHolder materials, Vec3 offset, int tick) {
        Matrix4fc transform = ChestRenderer.modelTransformation(state.facing).getMatrix();

        Vector3f pos = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).add(offset).toVector3f()
                .add(transform.getTranslation(new Vector3f()));

        Quaternionf rot = transform.getNormalizedRotation(new Quaternionf());

        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS_ROT, pos, rot, null);

        ChestModel model = getModel(state);
        setupAnim(model, state);

        // Under root, we have three model parts for each different variant, all hidden by default.
        String variant = variantRoot(state.type);

        ModelParts.captureModelPose(model.root(), variant, AnimationCurve.CurveFormat.POS_ROT, capture, tick, true);
        captureBaseModel(variant, state, capture, materials);
    }

    @Override
    protected void setupAnim(ChestModel model, ChestRenderState state) {
        // IDK ChestRenderer does this
        float open =  1 -state.open;
        open = 1 - open * open * open;
        model.setupAnim(open);
    }

    @Override
    protected Identifier getTexture(ChestRenderState state) {
        SpriteId sprite = Sheets.chooseSprite(state.material, state.type);
        return SpriteSource.TEXTURE_ID_CONVERTER.idToFile(sprite.texture());
    }
}