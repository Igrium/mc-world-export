package com.igrium.worldexport.blockentity.model_adapters;

import com.igrium.worldexport.blockentity.ModelBlockModelAdapter;
import com.igrium.worldexport.mixin.AccessorBellRenderer;
import net.minecraft.client.model.object.bell.BellModel;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BellRenderState;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BellModelAdapter extends ModelBlockModelAdapter<BellBlockEntity, BellRenderState, BellModel> {


    /**
     * Attempt to create a bell model adapter from an arbitrary block entity renderer.
     * This method exists to deal with all the bullshit surrounding generics in regard to renderers.
     *
     * @param renderer The renderer to adapt.
     * @param <T>      Block entity type.
     * @return The created model adapter.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends BlockEntity> BellModelAdapter fromRenderer(BlockEntityRenderer<? super T, ?> renderer) {
        // Yeah, this is probably unsafe, but I'm so tired of this generic bullshit that whatever.
        return new BellModelAdapter((BlockEntityRenderer) renderer);
    }

    public BellModelAdapter(BlockEntityRenderer<? super BellBlockEntity, BellRenderState> renderer) {
        super(renderer);
    }

    @Override
    protected BellModel getModel(BellRenderState state) {
        return ((AccessorBellRenderer) getRenderer()).getModel();
    }

    @Override
    protected void setupAnim(BellModel model, BellRenderState state) {
        model.setupAnim(new BellModel.State(state.ticks, state.shakeDirection));
    }

    @Override
    protected Identifier getTexture(BellRenderState state) {
        return SpriteSource.TEXTURE_ID_CONVERTER.idToFile(BellRenderer.BELL_TEXTURE.texture());
    }
}
