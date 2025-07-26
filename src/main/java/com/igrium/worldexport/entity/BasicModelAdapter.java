package com.igrium.worldexport.entity;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.ReplayTexture;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Math;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BasicModelAdapter<T extends Entity> extends ModelAdapter<T, EntityRenderState> {
    protected BasicModelAdapter(EntityRenderer<? super T, ? extends EntityRenderState> renderer) {
        super(renderer);
    }

    private final EntityRenderState defaultState = new EntityRenderState();

    public void capture(T entity, EntityRenderState state, CapturedEntity capture, MaterialHolder materials, Vec3d offset, int tick) {
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
