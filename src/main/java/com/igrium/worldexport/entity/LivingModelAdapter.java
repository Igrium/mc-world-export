package com.igrium.worldexport.entity;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.mixin.AccessorLivingEntityRenderer;
import de.javagl.obj.Objs;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class LivingModelAdapter<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends ModelAdapter<T, S> implements FeatureRendererContext<S, M> {


    /**
     * Attempt to create a living model adapter by casting an <code>EntityRenderer</code> to <code>LivingEntityRenderer</code>
     * This method exists to deal with all the bullshit surrounding generics in regard to renderers
     *
     * @param renderer The renderer to attempt to cast.
     * @param <T>      Entity type.
     * @param <S>      Render state type.
     * @return The created model adapter.
     * @throws ClassCastException If the supplied renderer is not an instance of <code>LivingEntityRenderer</code>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity, S extends EntityRenderState> ModelAdapter<T, S> fromEntityRenderer(EntityRenderer<? super T, ? extends S> renderer) throws ClassCastException {
        // There has to be a better way to handle this generic shitshow.
        // The logic here is that if the cast succeeds, then T and S must fit the requirements.
        // It's not like we reveal them to the calling class anyway.
        return new LivingModelAdapter<>((LivingEntityRenderer) renderer);
    }

    /**
     * The model gets set each capture based on the vanilla renderer's model.
     */
    @Nullable @Setter(AccessLevel.PROTECTED)
    private M model;

    @Getter @Setter @NonNull
    private AnimationCurve.CurveFormat curveFormat = AnimationCurve.CurveFormat.POS_ROT;

    @Getter
    private final LivingEntityRenderer<? super T, S, M> renderer;

    public LivingModelAdapter(LivingEntityRenderer<? super T, S, M> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    public LivingModelAdapter(LivingEntityRenderer<T, S, M> renderer, AnimationCurve.@NotNull CurveFormat curveFormat) {
        this(renderer);
        this.curveFormat = curveFormat;
    }

    @Override
    public void capture(T entity, S state, CapturedEntity capture, Vec3d offset, int tick) {
        AccessorLivingEntityRenderer<? super T, S, M> rendererAccessor = getRendererAccessor(renderer);

        M model = renderer.getModel();
        setModel(model);

        Vec3d pos = renderer.getPositionOffset(state).add(offset).add(entity.getPos());
        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS, pos.toVector3f(), null, null);

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.push();


        if (state.isInPose(EntityPose.SLEEPING)) {
            Direction direction = state.sleepingDirection;
            if (direction != null) {
                float f = state.standingEyeHeight - 0.1f;
                matrixStack.translate(-direction.getOffsetX() * f, 0, -direction.getOffsetZ() * f);
            }
        }

        float scale = state.baseScale;
        matrixStack.scale(scale, scale, scale);
        rendererAccessor.invokeSetupTransforms(state, matrixStack, state.bodyYaw, scale);

        matrixStack.scale(-1, -1, 1);
        rendererAccessor.invokeScale(state, matrixStack);
        matrixStack.translate(0, -1.501, 0); // TODO: If this causes the same problem as last time, remove it.

        model.setAngles(state);

        boolean isVisible = rendererAccessor.invokeIsVisible(state);
        boolean translucent = !isVisible && !state.invisibleToPlayer;

        ModelParts.captureModelPose(model.getRootPart(), "root", curveFormat, capture, tick, true);

        // Add transform to root bone
        AnimationCurve rootCurve = capture.getCurve("root", tick);
        if (rootCurve != null) {
            Matrix4f transform = matrixStack.peek().getPositionMatrix();
            Vector3f rootPos = new Vector3f();
            Quaternionf rootRot = rootCurve.hasRotation() ? new Quaternionf() : null;
            Vector3f rootScale = rootCurve.hasScale() ? new Vector3f() : null;

            rootPos.add(transform.getTranslation(new Vector3f()));
            if (rootRot != null)
                rootRot.mul(transform.getUnnormalizedRotation(new Quaternionf()));

            if (rootScale != null) {
                rootScale.mul(transform.getScale(new Vector3f()));
            }

//            rootCurve.setFrame(tick, rootPos, rootRot, rootScale);
            capture.addFrame("root", tick, rootCurve.getFormat(), rootPos, rootRot, rootScale);
        }


        // TODO: Check if doing this every frame causes performance issues.
        ModelParts.buildParentHierarchy(model.getRootPart(), "root", capture.getParents()::put);

        // Add part meshes if needed.
        ModelParts.forEachPart(model.getRootPart(), "root", (path, part) -> {
            capture.getModelParts().computeIfAbsent(path, p -> ModelParts.modelPartToMesh(part, Objs.create()));
        });

    }

    @Override
    public M getModel() {
        if (model == null) {
            throw new NullPointerException("Model must be initialized before getModel is called.");
        }
        return model;
    }

    /**
     * Utility method to cast LivingEntityRenderer to its accessor while dealing with all the generic bullshit.
     */
    @SuppressWarnings("unchecked")
    protected static <T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> AccessorLivingEntityRenderer<T, S, M> getRendererAccessor(LivingEntityRenderer<T, S, M> renderer) {
        return ((AccessorLivingEntityRenderer<T, S, M>) renderer);
    }
}
