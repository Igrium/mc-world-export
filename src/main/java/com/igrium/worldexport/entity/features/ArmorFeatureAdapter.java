package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.ModelParts;
import com.igrium.worldexport.mixin.AccessorArmorFeatureRenderer;
import com.igrium.worldexport.mixin.AccessorEquipmentRenderer;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.NativeImageReplayTexture;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.TextureExtractor;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;

public class ArmorFeatureAdapter<S extends BipedEntityRenderState, M extends BipedEntityModel<S>, A extends BipedEntityModel<S>> extends FeatureAdapter<S, M> {

    @SuppressWarnings({"unchecked", "rawtypes"}) // M and A are only used internally, so as long as they're self-consistent, their value doesn't matter.
    public static <S extends EntityRenderState> FeatureAdapter<S, ?> create(FeatureRenderer<S, ?> renderer) {
        return new ArmorFeatureAdapter<>((ArmorFeatureRenderer) renderer); // If render properly casts to ArmorFeatureRenderer, then S must be in-bounds.
    }

    private final ArmorFeatureRenderer<S, M, A> renderer;

    public ArmorFeatureAdapter(ArmorFeatureRenderer<S, M, A> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    private static boolean hasModel(EquippableComponent component, EquipmentSlot slot) {
        return component.assetId().isPresent() && component.slot() == slot;
    }

    protected EquipmentRenderer getEquipmentRenderer() {
        return renderAccessor().getEquipmentRenderer();
    }

    private A getModel(S state, EquipmentSlot slot) {
        return renderAccessor().invokeGetModel(state, slot);
    }

    @Override
    public void capture(CapturedEntity capture, MaterialHolder materials, S state, float limbAngle, float limbDistance, int tick) {
        captureSlot(capture, materials, state.equippedChestStack, EquipmentSlot.CHEST, getModel(state, EquipmentSlot.CHEST), tick);
        captureSlot(capture, materials, state.equippedLegsStack, EquipmentSlot.LEGS, getModel(state, EquipmentSlot.LEGS), tick);
        captureSlot(capture, materials, state.equippedFeetStack, EquipmentSlot.FEET, getModel(state, EquipmentSlot.FEET), tick);
        captureSlot(capture, materials, state.equippedHeadStack, EquipmentSlot.HEAD, getModel(state, EquipmentSlot.HEAD), tick);
    }

    private void captureSlot(CapturedEntity capture, MaterialHolder materials, ItemStack stack, EquipmentSlot slot, A armorModel, int tick) {
        EquippableComponent component = stack.get(DataComponentTypes.EQUIPPABLE);
        if (component != null && hasModel(component, slot)) {
            EquipmentModel.LayerType layerType = slot == EquipmentSlot.LEGS ?
                    EquipmentModel.LayerType.HUMANOID_LEGGINGS : EquipmentModel.LayerType.HUMANOID;

            List<EquipmentModel.Layer> layers = ((AccessorEquipmentRenderer) getEquipmentRenderer()).getEquipmentModelLoader()
                    .get(component.assetId().orElseThrow()).getLayers(layerType);

            if (layers.isEmpty())
                return;

            Identifier texId = layers.get(0).getFullTextureId(layerType);
            String texName = texId.getNamespace() + "/" + texId.getPath();
            String texPath = texName.endsWith(".png") ? texName : texName + ".png";

            materials.getTextures().computeIfAbsent(texPath, tex ->
                    TextureExtractor.pullTextureAsync(texId).thenApply(NativeImageReplayTexture::new));

            ReplayMtl mat = materials.getOrCreateMtl("entities.mtl", texName, n -> {
                ReplayMtl mtl = new ReplayMtl(Mtls.create(n));
                mtl.mtl().setMapKd(texPath);
                mtl.mtl().setMapD(texPath);
                mtl.properties().put("armor", ReplayMtl.Property.of(true));
                return mtl;
            });

            renderAccessor().invokeSetVisible(armorModel, slot);

            // Create part meshes
            ModelParts.forEachPart(armorModel.getRootPart(), "root", (path, part) -> {
                String armorPath = Registries.ITEM.getId(stack.getItem()) + "." + path;
                capture.getModelParts().computeIfAbsent(armorPath, p -> {
                    Obj obj = Objs.create();
                    obj.setMtlFileNames(Collections.singleton("entities.mtl"));
                    obj.setActiveMaterialGroupName(mat.getName());

                    capture.getParents().put(armorPath, path);
                    return ModelParts.modelPartToMesh(part, obj);
                });

                // Bogus frame to make sure it stays visible
                capture.addFrame(armorPath, tick, AnimationCurve.CurveFormat.POS, POS_IDENTITY, null, null);
            }, p -> p.visible);
        }
    }

    private static final Vector3f POS_IDENTITY = new Vector3f();

    @SuppressWarnings("unchecked")
    private AccessorArmorFeatureRenderer<S, M, A> renderAccessor() {
        return (AccessorArmorFeatureRenderer<S, M, A>) renderer;
    }
}
