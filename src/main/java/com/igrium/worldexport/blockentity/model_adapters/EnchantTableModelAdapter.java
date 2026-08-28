package com.igrium.worldexport.blockentity.model_adapters;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.blockentity.ModelBlockModelAdapter;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.mixin.AccessorEnchantTableRenderer;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class EnchantTableModelAdapter extends ModelBlockModelAdapter<EnchantingTableBlockEntity, EnchantTableRenderState, BookModel> {

    /**
     * Attempt to create an enchant table model adapter from an arbitrary block entity renderer.
     *
     * @param renderer The renderer to adapt.
     * @return The created model adapter.
     */
    public static EnchantTableModelAdapter fromRenderer(BlockEntityRenderer<?, ?> renderer) {
        return new EnchantTableModelAdapter((EnchantTableRenderer) renderer);
    }

    public EnchantTableModelAdapter(BlockEntityRenderer<? super EnchantingTableBlockEntity, EnchantTableRenderState> renderer) {
        super(renderer);
    }

    @Override
    protected void setupTransforms(EnchantingTableBlockEntity blockEntity, EnchantTableRenderState state, CapturedEntity capture, Vec3 offset, int tick) {
        float yOffset = 0.1F + Mth.sin(state.time * 0.1F) * 0.01F;
        Vector3f pos = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).add(offset).toVector3f()
                .add(0.5F, 0.75F + yOffset, 0.5F);

        Quaternionf rot = new Quaternionf().rotateY(-state.yRot).rotateZ(Mth.DEG_TO_RAD * 80.0F);

        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS_ROT, pos, rot, null);
    }

    @Override
    protected BookModel getModel(EnchantTableRenderState state) {
        return ((AccessorEnchantTableRenderer) getRenderer()).getBookModel();
    }

    @Override
    protected void setupAnim(BookModel model, EnchantTableRenderState state) {
        float ff1 = Mth.frac(state.flip + 0.25F) * 1.6F - 0.3F;
        float ff2 = Mth.frac(state.flip + 0.75F) * 1.6F - 0.3F;
        BookModel.State bookState = BookModel.State.forAnimation(state.time, Mth.clamp(ff1, 0.0F, 1.0F), Mth.clamp(ff2, 0.0F, 1.0F), state.open);
        model.setupAnim(bookState);
    }

    @Override
    protected Identifier getTexture(EnchantTableRenderState state) {
        return SpriteSource.TEXTURE_ID_CONVERTER.idToFile(EnchantTableRenderer.BOOK_TEXTURE.texture());
    }
}
