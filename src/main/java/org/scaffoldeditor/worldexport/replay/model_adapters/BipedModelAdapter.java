package org.scaffoldeditor.worldexport.replay.model_adapters;

import java.util.HashMap;
import java.util.Map;

import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;

import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

/**
 * An animal model adapter that can render items and armor
 */
public class BipedModelAdapter<T extends LivingEntity> extends AnimalModelAdapter<T> {

    public static class BipedModelFactory<U extends LivingEntity> implements ReplayModelAdapterFactory<U> {

        Identifier texture;

        public BipedModelFactory(Identifier texture) {
            this.texture = texture;
        }

        @Override
        public BipedModelAdapter<U> create(U entity) {
            return new BipedModelAdapter<U>(entity, texture, ReplayModels.BIPED_Y_OFFSET);
        }

    }

    public BipedModelAdapter(T entity, Identifier texture, float yOffset) throws IllegalArgumentException {
        super(entity, texture, yOffset);
    }

    protected Map<Item, ReplayModelPart> leftHandModels = new HashMap<>();
    protected Map<Item, ReplayModelPart> rightHandModels = new HashMap<>();


    protected HeldItemFeatureAdapter heldItemAdapter;

    @Override
    protected MultipartReplayModel captureBaseModel(AnimalModel<T> model) {
        MultipartReplayModel rModel = super.captureBaseModel(model);
        heldItemAdapter = new HeldItemFeatureAdapter(getEntity(), rModel);
        return rModel;
    }
    
    @Override
    protected Pose<ReplayModelPart> writePose(float tickDelta) {
        Pose<ReplayModelPart> pose = super.writePose(tickDelta);
        heldItemAdapter.writePose(pose, tickDelta);

        return pose;
    }

    @Override
    public void generateMaterials(MaterialConsumer file) {
        super.generateMaterials(file);
        heldItemAdapter.generateMaterials(file);
    }
}
