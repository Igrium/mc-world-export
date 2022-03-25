package org.scaffoldeditor.worldexport.replay.model_adapters;

import org.scaffoldeditor.worldexport.replay.models.ArmatureReplayModel;

import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.SheepWoolEntityModel;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.util.Identifier;

public class SheepModelAdapter extends AnimalModelAdapter<SheepEntity> {

    private static final Identifier TEXTURE = new Identifier("textures/entity/sheep/sheep.png");
    private static final Identifier SKIN = new Identifier("textures/entity/sheep/sheep_fur.png");

    protected AnimalModelAdapter<SheepEntity> wool;

    public SheepModelAdapter(SheepEntity entity, float yOffset) throws IllegalArgumentException {
        super(entity, TEXTURE, yOffset);
        
    }
    
    @Override
    protected ArmatureReplayModel captureBaseModel(AnimalModel<SheepEntity> model) {
        ArmatureReplayModel replayModel = super.captureBaseModel(model);
        return replayModel;
    }
}
