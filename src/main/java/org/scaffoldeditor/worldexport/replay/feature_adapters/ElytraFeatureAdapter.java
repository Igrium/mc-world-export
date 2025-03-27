package org.scaffoldeditor.worldexport.replay.feature_adapters;

import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;

public class ElytraFeatureAdapter implements ReplayFeatureAdapter<ReplayModelPart> {

    private boolean wasElytraVisible;

    private static final Identifier SKIN = new Identifier("textures/entity/elytra.png");
    private ElytraEntityModel<PlayerEntity> elytra;

    @Override
    public void writePose(ReplayModel.Pose<ReplayModelPart> pose, float tickDelta) {

    }

    @Override
    public void generateMaterials(MaterialConsumer consumer) {

    }
}
