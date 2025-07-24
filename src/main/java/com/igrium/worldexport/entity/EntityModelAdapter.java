package com.igrium.worldexport.entity;

import com.igrium.worldexport.anim.AnimationCurve;
import lombok.Getter;

import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Math;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Handles the export of a single entity type. Analogous to {@link EntityRenderer}.
 *
 * @param <T> Type of entity to capture.
 * @param <S> That entity's render state.
 * @apiNote Unlike entity renderers, one instance exists <em>per exported replay</em>
 */
public class EntityModelAdapter<T extends Entity, S extends EntityRenderState> {

    /**
     * An offset to apply to the position of all entities.
     */
    @Getter @Setter @NonNull
    private Vec3d globalOffset = Vec3d.ZERO;

    /**
     * Capture the entity's current pose.
     *
     * @param entity  Entity to capture the pose of.
     * @param capture Animation to insert the pose.
     * @param tick    The current tick index in the replay.
     */
    public void capture(T entity, CapturedEntity capture, int tick) {
        Vec3d pos = entity.getPos().add(globalOffset);
        float yRot = Math.toRadians(entity.getYaw());
        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS_ROT,
                pos.toVector3f(), new Quaternionf().rotateY(yRot), new Vector3f(1));
    }
}
