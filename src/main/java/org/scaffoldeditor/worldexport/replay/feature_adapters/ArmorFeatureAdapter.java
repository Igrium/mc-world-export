package org.scaffoldeditor.worldexport.replay.feature_adapters;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.MaterialUtils;
import org.scaffoldeditor.worldexport.mat.PromisedReplayTexture;
import org.scaffoldeditor.worldexport.mat.ReplayTexture;
import org.scaffoldeditor.worldexport.replay.model_adapters.BipedModelAdapter;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.util.MeshUtils;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class ArmorFeatureAdapter implements ReplayFeatureAdapter<ReplayModelPart> {
    final BipedModelAdapter<?> baseModel;
    BipedEntityModel<?> armorModel;
    BipedEntityModel<?> leggingsModel;

    protected Map<ArmorItem, ArmorModelEntry> armorModels = new HashMap<>();
    protected Set<Identifier> armorTextures = new HashSet<>();

    private final Map<EquipmentSlot, Iterable<ReplayModelPart>> lastModels = new HashMap<>();

    public ArmorFeatureAdapter(BipedModelAdapter<?> baseModel, BipedEntityModel<?> leggingsModel, BipedEntityModel<?> armorModel) {
        this.baseModel = baseModel;
        this.armorModel = armorModel;
        this.leggingsModel = leggingsModel;
    }

    @Override
    public void writePose(Pose<ReplayModelPart> pose, float tickDelta) {
        writeSlot(pose, EquipmentSlot.HEAD);
        writeSlot(pose, EquipmentSlot.CHEST);
        writeSlot(pose, EquipmentSlot.LEGS);
        writeSlot(pose, EquipmentSlot.FEET);
    }
    
    private void writeSlot(Pose<ReplayModelPart> pose, EquipmentSlot slot) {
        // Clean up from last frame. Will be overwritten if part should remain visible.
        if (lastModels.containsKey(slot)) {
            for (ReplayModelPart part : lastModels.get(slot)) {
                pose.bones.put(part, new Transform(false));
            }
        }

        ItemStack itemStack = getEntity().getEquippedStack(slot);
        if (!(itemStack.getItem() instanceof ArmorItem item)) return;
        if (item.getSlotType() != slot) return;

        ArmorModelEntry entry = armorModels.get(item);
        if (entry == null) {
            entry = genArmorModel(item);
        }

        for (ReplayModelPart part : entry) {
            pose.bones.put(part, new Transform(true));
        }
        lastModels.put(slot, entry);

    }

    private ArmorModelEntry genArmorModel(ArmorItem item) {
        EquipmentSlot slot = item.getSlotType();

        Identifier texID = getArmorTexture(item, slot == EquipmentSlot.LEGS);
        armorTextures.add(texID);
        String texture = MaterialUtils.getTexName(texID);

        ArmorModelEntry entry = new ArmorModelEntry();
        String name = Registries.ITEM.getId(item).toString();

        if (slot == EquipmentSlot.HEAD) {
            entry.head = new ReplayModelPart(name+".head");
            entry.head.getMesh().setActiveMaterialGroupName(texture);
            MeshUtils.appendModelPart(armorModel.head, entry.head.getMesh(), false, null);
            baseModel.getHead().children.add(entry.head);

        } else if (slot == EquipmentSlot.CHEST) {
            entry.body = new ReplayModelPart(name+".body");
            entry.body.getMesh().setActiveMaterialGroupName(texture);
            MeshUtils.appendModelPart(armorModel.body, entry.body.getMesh(), false, null);
            baseModel.getBody().children.add(entry.body);

            entry.leftArm = new ReplayModelPart(name+".left_arm");
            entry.leftArm.getMesh().setActiveMaterialGroupName(texture);
            MeshUtils.appendModelPart(armorModel.leftArm, entry.leftArm.getMesh(), false, null);
            baseModel.getLeftArm().children.add(entry.leftArm);

            entry.rightArm = new ReplayModelPart(name+".right_arm");
            entry.rightArm.getMesh().setActiveMaterialGroupName(texture);
            MeshUtils.appendModelPart(armorModel.rightArm, entry.rightArm.getMesh(), false, null);
            baseModel.getRightArm().children.add(entry.rightArm);

        } else if (slot == EquipmentSlot.LEGS) {
            entry.body = new ReplayModelPart(name+".crotch");
            entry.body.getMesh().setActiveMaterialGroupName(texture);
            MeshUtils.appendModelPart(leggingsModel.body, entry.body.getMesh(), false, null);
            baseModel.getBody().children.add(entry.body);

            entry.leftLeg = new ReplayModelPart(name+".left_leg");
            entry.leftLeg.getMesh().setActiveMaterialGroupName(texture);
            MeshUtils.appendModelPart(leggingsModel.leftLeg, entry.leftLeg.getMesh(), false, null);
            baseModel.getLeftLeg().children.add(entry.leftLeg);

            entry.rightLeg = new ReplayModelPart(name+".right_leg");
            entry.rightLeg.getMesh().setActiveMaterialGroupName(texture);
            MeshUtils.appendModelPart(leggingsModel.rightLeg, entry.rightLeg.getMesh(), false, null);
            baseModel.getRightLeg().children.add(entry.rightLeg);

        } else if (slot == EquipmentSlot.FEET) {
            entry.leftLeg = new ReplayModelPart(name+".left_foot");
            entry.leftLeg.getMesh().setActiveMaterialGroupName(texture);
            MeshUtils.appendModelPart(armorModel.leftLeg, entry.leftLeg.getMesh(), false, null);
            baseModel.getLeftLeg().children.add(entry.leftLeg);

            entry.rightLeg = new ReplayModelPart(name+".right_foot");
            entry.rightLeg.getMesh().setActiveMaterialGroupName(texture);
            MeshUtils.appendModelPart(armorModel.rightLeg, entry.rightLeg.getMesh(), false, null);
            baseModel.getRightLeg().children.add(entry.rightLeg);
        }

        armorModels.put(item, entry);
        
        return entry;
    }

    @Override
    public void generateMaterials(MaterialConsumer consumer) {
        for (Identifier texture : armorTextures) {
            String texName = MaterialUtils.getTexName(texture);

            Material material = new Material();
            material.setColor(texName);
            material.setRoughness(1);
            material.setTransparent(true);
            material.addOverride("metallic", "metal_test");

            ReplayTexture rTexture = new PromisedReplayTexture(texture);

            consumer.addTexture(texName, rTexture);
            consumer.addMaterial(texName, material);
        }
    }

    public LivingEntity getEntity() {
        return baseModel.getEntity();
    }

    private static class ArmorModelEntry implements Iterable<ReplayModelPart> {
        public ReplayModelPart head;
        public ReplayModelPart body;
        public ReplayModelPart leftArm;
        public ReplayModelPart rightArm;
        public ReplayModelPart leftLeg;
        public ReplayModelPart rightLeg;

        @Override
        public int hashCode() {
            return Objects.hash(head, body, leftArm, rightArm, leftLeg, rightLeg);
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof ArmorModelEntry && this.hashCode() == obj.hashCode());
        }

        @Override
        public Iterator<ReplayModelPart> iterator() {
            return Arrays.stream(new ReplayModelPart[] { head, body, leftArm, rightArm, leftLeg, rightLeg }).iterator();
        }
    }

    private Identifier getArmorTexture(ArmorItem item, boolean legs) {
        return new Identifier("textures/models/armor/" + item.getMaterial().getName() + "_layer_" + (legs ? 2 : 1) + ".png");
    }
}
