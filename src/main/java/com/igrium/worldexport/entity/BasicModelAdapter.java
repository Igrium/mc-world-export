package com.igrium.worldexport.entity;

import com.igrium.worldexport.anim.AnimationCurve;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;
import org.joml.Math;
import org.joml.Quaternionf;

public class BasicModelAdapter<T extends Entity> extends ModelAdapter<T, EntityRenderState> {
    protected BasicModelAdapter() {
        super(EntityRenderState.class);
    }

    private final EntityRenderState defaultState = new EntityRenderState();

    public void capture(T entity, EntityRenderState state, CapturedEntity capture, Vec3d offset, int tick) {
        Vec3d pos = entity.getPos().add(offset);
        float yRot = Math.toRadians(entity.getYaw());
        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS_ROT,
                pos.toVector3f(), new Quaternionf().rotateY(yRot), null);
    }

    @Override
    public EntityRenderState getAndUpdateRenderState(T entity) {
        return defaultState;
    }
}
