package com.igrium.worldexport.entity;

import com.igrium.worldexport.replay.MaterialHolder;
import lombok.Getter;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;


/**
 * Handles the export of a single entity type. Analogous to {@link EntityRenderer}.
 *
 * @param <T> Type of entity to capture.
 * @param <S> That entity's render state.
 * @apiNote One instance exists <em>per exported replay</em>
 */
public abstract class ModelAdapter<T extends Entity, S extends EntityRenderState> {


    @Getter
    private final EntityRenderer<? super T, ? extends S> renderer;

    protected ModelAdapter(EntityRenderer<? super T, ? extends S> renderer) {
        this.renderer = renderer;
    }

    public S getAndUpdateRenderState(T entity) {
        return renderer.createRenderState(entity, 1);
    }

    /**
     * Capture the entity's current pose.
     *
     * @param entity    Entity to capture the pose of.
     * @param state     Render state of the entity.
     * @param capture   Animation to insert the pose.
     * @param materials All the materials this replay has.
     * @param offset    An offset to apply to the position of the entity. Used when the replay is not centered on 0,0,0.
     * @param tick      The current tick index in the replay.
     */
    public abstract void capture(T entity, S state, CapturedEntity capture, MaterialHolder materials, Vec3 offset, int tick);

}
