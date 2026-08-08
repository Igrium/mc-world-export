package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.ModelParts;
import com.igrium.worldexport.mixin.AccessorHumanoidArmorLayer;
import com.igrium.worldexport.mixin.AccessorEquipmentLayerRenderer;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.NativeImageReplayTexture;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.TextureExtractor;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;

public class ArmorFeatureAdapter<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> extends FeatureAdapter<S, M> {

    @SuppressWarnings({"unchecked", "rawtypes"}) // M and A are only used internally, so as long as they're self-consistent, their value doesn't matter.
    public static <S extends EntityRenderState> FeatureAdapter<S, ?> create(RenderLayer<S, ?> renderer) {
        return new ArmorFeatureAdapter<>((HumanoidArmorLayer) renderer); // If render properly casts to ArmorFeatureRenderer, then S must be in-bounds.
    }

    private final HumanoidArmorLayer<S, M, A> renderer;

    public ArmorFeatureAdapter(HumanoidArmorLayer<S, M, A> renderer) {
        super(renderer);
        this.renderer = renderer;
    }

    private static boolean hasModel(Equippable component, EquipmentSlot slot) {
        return component.assetId().isPresent() && component.slot() == slot;
    }

    protected EquipmentLayerRenderer getEquipmentRenderer() {
        return renderAccessor().getEquipmentRenderer();
    }

    private A getModel(S state, EquipmentSlot slot) {
        return renderAccessor().invokeGetArmorModel(state, slot);
    }

    @Override
    public void capture(CapturedEntity capture, MaterialHolder materials, S state, float limbAngle, float limbDistance, int tick) {
        captureSlot(capture, materials, state.chestEquipment, EquipmentSlot.CHEST, getModel(state, EquipmentSlot.CHEST), tick);
        captureSlot(capture, materials, state.legsEquipment, EquipmentSlot.LEGS, getModel(state, EquipmentSlot.LEGS), tick);
        captureSlot(capture, materials, state.feetEquipment, EquipmentSlot.FEET, getModel(state, EquipmentSlot.FEET), tick);
        captureSlot(capture, materials, state.headEquipment, EquipmentSlot.HEAD, getModel(state, EquipmentSlot.HEAD), tick);
    }

    private void captureSlot(CapturedEntity capture, MaterialHolder materials, ItemStack stack, EquipmentSlot slot, A armorModel, int tick) {
        Equippable component = stack.get(DataComponents.EQUIPPABLE);
        if (component != null && hasModel(component, slot)) {
            EquipmentClientInfo.LayerType layerType = slot == EquipmentSlot.LEGS ?
                    EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;

            List<EquipmentClientInfo.Layer> layers = ((AccessorEquipmentLayerRenderer) getEquipmentRenderer()).getEquipmentAssets()
                    .get(component.assetId().orElseThrow()).getLayers(layerType);

            if (layers.isEmpty())
                return;

            Identifier texId = layers.getFirst().getTextureLocation(layerType);
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

            // Create part meshes
            ModelParts.forEachPart(armorModel.root(), "root", (path, part) -> {
                String armorPath = BuiltInRegistries.ITEM.getKey(stack.getItem()) + "." + path;
                capture.getModelParts().computeIfAbsent(armorPath, p -> {
                    Obj obj = Objs.create();
                    obj.setMtlFileNames(Collections.singleton("entities.mtl"));
                    obj.setActiveMaterialGroupName(mat.getName());

                    capture.getParents().put(armorPath, path);
                    return ModelParts.modelPartToMesh(part, obj);
                });

                // Bogus frame to make sure it stays visible
                capture.addFrame(armorPath, tick, AnimationCurve.CurveFormat.EMPTY, null, null, null);
            }, p -> p.visible);
        }
    }

    @SuppressWarnings("unchecked")
    private AccessorHumanoidArmorLayer<S, M, A> renderAccessor() {
        return (AccessorHumanoidArmorLayer<S, M, A>) renderer;
    }
}
