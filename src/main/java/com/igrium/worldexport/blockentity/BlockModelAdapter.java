package com.igrium.worldexport.blockentity;

import com.igrium.worldexport.entity.CapturedEntity;
import lombok.Getter;
import com.igrium.worldexport.replay.MaterialHolder;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Handles the export of a single block entity type. Analogous to {@link BlockEntityRenderer}.
 * <p>
 * Only handles dynamic elements of the block entity. Static elements are meshed with the world.
 *
 * @param <T> Type of block entity to capture.
 * @param <S> That block entity's render state.
 * @apiNote One instance exists <em>per exported replay</em>
 */
public abstract class BlockModelAdapter<T extends BlockEntity, S extends BlockEntityRenderState> {
    @Getter
    private final BlockEntityRenderer<? super T, S> renderer;

    public BlockModelAdapter(BlockEntityRenderer<? super T, S> renderer) {
        this.renderer = renderer;
    }

    public S getAndUpdateRenderState(T blockEntity, Vec3 cameraPos) {
        S state = renderer.createRenderState();
        // TODO: why does this need a camera pos? What should we pass?
        renderer.extractRenderState(blockEntity, state, 1, cameraPos, null);
        return state;
    }

    /**
     * Capture the block entity's current pose.
     *
     * @param blockEntity Entity to capture the pose of.
     * @param state       Render state of the entity.
     * @param capture     Animation to insert the pose.
     * @param materials   All the materials this replay has.
     * @param offset      An offset to apply to the position of the entity. Used when the replay is not centered on
     *                    0,0,0.
     * @param tick        The current tick index in the replay.
     */
    public abstract void capture(T blockEntity, S state, CapturedEntity capture,
                                 MaterialHolder materials, Vec3 offset, int tick);
}
