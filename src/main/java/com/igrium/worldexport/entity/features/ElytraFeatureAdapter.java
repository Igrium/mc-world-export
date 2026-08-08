package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.ModelParts;
import com.igrium.worldexport.mixin.AccessorEquipmentLayerRenderer;
import com.igrium.worldexport.mixin.AccessorWingsLayer;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.ReplayMtl;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;

/**
 * Captures the elytra rendered by {@link WingsLayer}.
 */
public class ElytraFeatureAdapter<S extends HumanoidRenderState, M extends EntityModel<S>> extends FeatureAdapter<S, M> {

    /**
     * Vanilla offsets the elytra away from the body before rendering it.
     */
    private static final float Z_OFFSET = 0.125f;

    /**
     * The bone the elytra is attached to. Vanilla renders the elytra in raw model space, but
     * parenting it to the body makes it far easier to work with in Blender.
     */
    private static final String PARENT_BONE = "root/body";

    @SuppressWarnings({"unchecked", "rawtypes"}) // M is only used internally, so as long as it's self-consistent, its value doesn't matter.
    public static <S extends EntityRenderState> FeatureAdapter<S, ?> create(RenderLayer<S, ?> renderer) {
        return new ElytraFeatureAdapter<>((WingsLayer) renderer); // If renderer properly casts to WingsLayer, then S must be in-bounds.
    }

    private final WingsLayer<S, M> renderer;

    public ElytraFeatureAdapter(WingsLayer<S, M> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void capture(CapturedEntity capture, MaterialHolder materials, S state, float limbAngle, float limbDistance, int tick) {
        ItemStack stack = state.chestEquipment;
        Equippable component = stack.get(DataComponents.EQUIPPABLE);
        if (component == null || component.assetId().isEmpty())
            return;

        List<EquipmentClientInfo.Layer> layers = ((AccessorEquipmentLayerRenderer) renderAccessor().getEquipmentRenderer())
                .getEquipmentAssets().get(component.assetId().orElseThrow())
                .getLayers(EquipmentClientInfo.LayerType.WINGS);

        if (layers.isEmpty())
            return;

        ReplayMtl mat = getEquipmentMaterial(materials, getTexture(layers.getFirst(), state));

        // The capture happens outside the render pass, so the wings haven't been posed for us.
        ElytraModel model = state.isBaby ? renderAccessor().getElytraBabyModel() : renderAccessor().getElytraModel();
        model.setupAnim(state);

        String rootName = BuiltInRegistries.ITEM.getKey(stack.getItem()) + (state.isBaby ? ".baby.root" : ".root");

        // Create part meshes
        ModelParts.forEachPart(model.root(), rootName, (path, part) -> {
            capture.getModelParts().computeIfAbsent(path, p -> {
                Obj obj = Objs.create();
                obj.setMtlFileNames(Collections.singleton("entities.mtl"));
                obj.setActiveMaterialGroupName(mat.getName());
                return ModelParts.modelPartToMesh(part, obj);
            });
        }, p -> p.visible);

        ModelParts.buildParentHierarchy(model.root(), rootName, capture.getParents()::put);
        // Not every model with a wings layer is guaranteed to name its torso the usual way.
        capture.getParents().put(rootName, capture.getCurves().containsKey(PARENT_BONE) ? PARENT_BONE : "root");

        ModelParts.captureModelPose(model.root(), rootName, AnimationCurve.CurveFormat.POS_ROT, capture, tick, true);
        capture.addFrame(rootName, tick, AnimationCurve.CurveFormat.POS_ROT,
                new Vector3f(0, 0, Z_OFFSET), new Quaternionf(), null);
    }

    /**
     * Resolve the texture the elytra renders with, honoring the player's elytra (or cape) skin.
     */
    private Identifier getTexture(EquipmentClientInfo.Layer layer, S state) {
        if (layer.usePlayerTexture()) {
            @Nullable Identifier playerTexture = AccessorWingsLayer.invokeGetPlayerElytraTexture(state);
            if (playerTexture != null)
                return playerTexture;
        }
        return layer.getTextureLocation(EquipmentClientInfo.LayerType.WINGS);
    }

    private AccessorWingsLayer renderAccessor() {
        return (AccessorWingsLayer) renderer;
    }
}