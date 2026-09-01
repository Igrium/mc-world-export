package com.igrium.worldexport.blockentity;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.replay.MaterialHolder;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class BasicBlockModelAdapter<T extends BlockEntity> extends BlockModelAdapter<T, BlockEntityRenderState> {

    public BasicBlockModelAdapter(BlockEntityRenderer<? super T, BlockEntityRenderState> renderer) {
        super(renderer);
    }

    private final BlockEntityRenderState defaultState = new BlockEntityRenderState();

    @Override
    public void capture(T blockEntity, BlockEntityRenderState state, CapturedEntity capture,
                        MaterialHolder materials, Vec3 offset, int tick) {
        Vec3 pos = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).add(offset);
        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS,
                pos.toVector3f(), null, null);
    }

    @Override
    public BlockEntityRenderState getAndUpdateRenderState(T blockEntity, Vec3 cameraPos) {
        return defaultState;
    }
}
