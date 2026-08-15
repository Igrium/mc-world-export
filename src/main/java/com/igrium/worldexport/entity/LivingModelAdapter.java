package com.igrium.worldexport.entity;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.features.FeatureAdapter;
import com.igrium.worldexport.entity.features.FeatureAdapters;
import com.igrium.worldexport.mixin.AccessorLivingEntityRenderer;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.TextureExtractor;
import com.mojang.blaze3d.vertex.PoseStack;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LivingModelAdapter<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends ModelAdapter<T, S> implements RenderLayerParent<S, M> {


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

    private final List<FeatureAdapter<S, ?>> features = new ArrayList<>();

    protected final void addFeature(@NonNull FeatureAdapter<S, ?> feature) {
        features.add(feature);
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

        for (var feature : getRendererAccessor(renderer).getLayers()) {
            var adapter = FeatureAdapters.create(feature);
            if (adapter != null)
                addFeature(adapter);
        }
    }

    public LivingModelAdapter(LivingEntityRenderer<T, S, M> renderer, @NonNull AnimationCurve.CurveFormat curveFormat) {
        this(renderer);
        this.curveFormat = curveFormat;
    }

    @Override
    public void capture(T entity, S state, CapturedEntity capture, MaterialHolder materials, Vec3 offset, int tick) {
        AccessorLivingEntityRenderer<? super T, S, M> rendererAccessor = getRendererAccessor(renderer);

        M model = renderer.getModel();
        setModel(model);

        Vec3 pos = renderer.getRenderOffset(state).add(offset).add(entity.position());
        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS, pos.toVector3f(), null, null);

        PoseStack matrixStack = new PoseStack();
        matrixStack.pushPose();


        if (state.hasPose(Pose.SLEEPING)) {
            Direction direction = state.bedOrientation;
            if (direction != null) {
                float f = state.eyeHeight - 0.1f;
                matrixStack.translate(-direction.getStepX() * f, 0, -direction.getStepZ() * f);
            }
        }

        float scale = state.scale;
        matrixStack.scale(scale, scale, scale);
        rendererAccessor.invokeSetupRotations(state, matrixStack, state.bodyRot, scale);

        matrixStack.scale(-1, -1, 1);
        rendererAccessor.invokeScale(state, matrixStack);
        matrixStack.translate(0, -1.501, 0); // TODO: If this causes the same problem as last time, remove it.

        model.setupAnim(state);

        boolean isVisible = rendererAccessor.invokeIsBodyVisible(state);
        boolean translucent = !isVisible && !state.isInvisibleToPlayer;

        ModelParts.captureModelPose(model.root(), "root", curveFormat, capture, tick, true);

        // Add transform to root bone
        AnimationCurve rootCurve = capture.getCurve("root", tick);
        if (rootCurve != null) {
            Matrix4f transform = matrixStack.last().pose();
            Vector3f rootPos = new Vector3f();
            Quaternionf rootRot = rootCurve.hasRotation() ? new Quaternionf() : null;
            Vector3f rootScale = rootCurve.hasScale() ? new Vector3f() : null;

            rootPos.add(transform.getTranslation(new Vector3f()));
            if (rootRot != null)
                rootRot.mul(transform.getUnnormalizedRotation(new Quaternionf()));

            if (rootScale != null) {
                rootScale.mul(transform.getScale(new Vector3f()));
            }

            capture.addFrame("root", tick, rootCurve.getFormat(), rootPos, rootRot, rootScale);
        }

        captureBaseModel(entity, state, capture, materials);

        if (getRendererAccessor(renderer).invokeShouldRenderLayers(state)) {
            for (var feature : features) {
                feature.capture(capture, materials, state, state.yRot, state.xRot, tick);
            }
        }
    }

    protected void captureBaseModel(T entity, S state, CapturedEntity capture, MaterialHolder materials) {
        // Extract texture
        Identifier texId = renderer.getTextureLocation(state);
        String texName = EntityCapture.getEntityTexturePath(texId);
        String texPath = texName.endsWith(".png") ? texName : texName + ".png";

        materials.getTextures().computeIfAbsent(texName, tex ->
                CompletableFuture.completedFuture(TextureExtractor.pullTexture(texId)));

        ReplayMtl mat = materials.getOrCreateMtl("entities.mtl", texName, n -> {
            ReplayMtl mtl = new ReplayMtl(Mtls.create(n));
            mtl.mtl().setMapKd(texPath);
            mtl.mtl().setMapD(texPath);
            mtl.properties().put("entityType", ReplayMtl.Property.of(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
            return mtl;
        });

        // TODO: Check if doing this every frame causes performance issues.
        if (model == null) return;
        ModelParts.buildParentHierarchy(model.root(), "root", capture.getParents()::put);

        // Add part meshes if needed.
        ModelParts.forEachPart(model.root(), "root", (path, part) -> {
            capture.getModelParts().computeIfAbsent(path, p -> {
                Obj obj = Objs.create();
                obj.setMtlFileNames(Collections.singleton("entities.mtl"));
                obj.setActiveMaterialGroupName(mat.getName());
                return ModelParts.modelPartToMesh(part, obj);
            });
        });
    }

    @Override
    public @NonNull M getModel() {
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
