package com.igrium.worldexport.entity;

import com.igrium.worldexport.mixin.AccessorEntityRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;

import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;


/**
 * Handles the export of a single entity type. Analogous to {@link EntityRenderer}.
 *
 * @param <T> Type of entity to capture.
 * @param <S> That entity's render state.
 * @apiNote Unlike entity renderers, one instance exists <em>per exported replay</em>
 */
public abstract class ModelAdapter<T extends Entity, S extends EntityRenderState> {

    private final Class<? extends S> renderStateClass;

    private S renderState;

    protected ModelAdapter(Class<? extends S> renderStateClass) {
        this.renderStateClass = renderStateClass;
    }

    /**
     * Get the entity's vanilla renderer in a format that is compatible with this model adapter.
     *
     * @param dispatcher Entity render dispatcher to use.
     * @param entity     Entity to get the renderer of.
     * @return The vanilla renderer.
     * @throws ClassCastException   If the vanilla renderer uses the wrong state type.
     * @throws NullPointerException If the supplied entity has no renderer.
     */
    @SuppressWarnings("unchecked")
    protected EntityRenderer<T, S> getRenderer(EntityRenderDispatcher dispatcher, T entity) throws ClassCastException, NullPointerException {
        var renderer = dispatcher.getRenderer(entity);
        if (renderer == null) {
            throw new NullPointerException("The supplied entity does not have a renderer!");
        }
        var state = ((AccessorEntityRenderer) renderer).getState();
        if (!renderStateClass.isInstance(state)) {
            throw new ClassCastException("The vanilla entity renderer uses the wrong state class.");
        }
        return (EntityRenderer<T, S>) renderer;
    }

    /**
     * Get the entity's vanilla renderer in a format that is compatible with this model adapter.
     *
     * @param entity Entity to get the renderer of.
     * @return The vanilla renderer.
     * @throws ClassCastException   If the vanilla renderer uses the wrong state type.
     * @throws NullPointerException If the supplied entity has no renderer.
     */
    protected EntityRenderer<T, S> getRenderer(T entity) throws ClassCastException, NullPointerException {
        return getRenderer(MinecraftClient.getInstance().getEntityRenderDispatcher(), entity);
    }

    public S getAndUpdateRenderState(T entity) {
        var renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer == null) {
            throw new NullPointerException("The supplied entity does not have a renderer!");
        }
        var state = renderer.getAndUpdateRenderState(entity, 1);
        return renderStateClass.cast(state);
    }


    /**
     * Capture the entity's current pose.
     *
     * @param entity  Entity to capture the pose of.
     * @param state   Render state of the entity.
     * @param capture Animation to insert the pose.
     * @param offset  An offset to apply to the position of the entity. Used when the replay is not centered on 0,0,0.
     * @param tick    The current tick index in the replay.
     */
    public abstract void capture(T entity, S state, CapturedEntity capture, Vec3d offset, int tick);

}
