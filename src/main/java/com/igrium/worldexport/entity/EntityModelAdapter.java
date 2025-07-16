package com.igrium.worldexport.entity;

import lombok.Getter;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;

public class EntityModelAdapter<T extends Entity, S extends EntityRenderState> {

    /**
     * The vanilla entity renderer for this entity. Used to delegate functions that don't directly have to do with meshing.
     */
    @Getter
    private final EntityRenderer<T, S> entityRenderer;

    private final S state;

    public EntityModelAdapter(EntityRenderer<T, S> entityRenderer) {
        this.entityRenderer = entityRenderer;
        state = entityRenderer.createRenderState();
    }
}
