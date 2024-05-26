package org.scaffoldeditor.worldexport.replay.model_adapters.specific;

import net.minecraft.client.render.model.json.ModelTransformationMode;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModelAdapter;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayItemRenderer;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.math.MatrixStack;


public class ItemModelAdapter implements ReplayModelAdapter<MultipartReplayModel> {

    MultipartReplayModel model;
    ReplayModelPart base;

    private final ItemEntity entity;
    private final ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
    private BakedModel itemModel;

    public ItemModelAdapter(ItemEntity entity) {
        this.entity = entity;
        genModel();
    }

    private void genModel() {
        model = new MultipartReplayModel();
        base = new ReplayModelPart("item");
        model.bones.add(base);

        ItemStack stack = getEntity().getStack();
        itemModel = itemRenderer.getModel(stack, entity.getWorld(), null, entity.getId());
        ReplayItemRenderer.renderItem(stack, ModelTransformationMode.GROUND, false, new MatrixStack(), base.getMesh(), itemModel);
    }

    @Override
    public MultipartReplayModel getModel() {
        return model;
    }

    @Override
    public void generateMaterials(MaterialConsumer file) {
        ReplayItemRenderer.addMaterials(file);
    }

    @Override
    public Pose<ReplayModelPart> getPose(float tickDelta) {
        Vector3d pos = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
        Quaterniond rot = new Quaterniond();

        double bobbing = Math.sin((entity.getItemAge() + tickDelta) / 10f + entity.uniqueOffset) * .1f + .1f;
        float modelYOffset = itemModel.getTransformation().getTransformation(ModelTransformationMode.GROUND).scale.y();
        pos.add(0, bobbing + .25 * modelYOffset, 0);

        rot.rotateY(entity.getRotation(tickDelta));

        Pose<ReplayModelPart> pose = new Pose<>();
        pose.root = new Transform(pos, rot, new Vector3d(1));
        pose.bones.put(base, Transform.NEUTRAL);

        return pose;
    }

    public ItemEntity getEntity() {
        return entity;
    }

    /**
     * Get the baked model that was used to generate the replay model.
     * @return item model.
     */
    public BakedModel getItemModel() {
        return itemModel;
    }
    
}
