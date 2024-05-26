package org.scaffoldeditor.worldexport.replay.model_adapters.specific;

import org.scaffoldeditor.worldexport.replay.model_adapters.AnimalModelAdapter;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;

import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.util.Identifier;

public class SheepModelAdapter extends AnimalModelAdapter<SheepEntity> {

    private static final Identifier TEXTURE = new Identifier("textures/entity/sheep/sheep.png");
    // private static final Identifier SKIN = new Identifier("textures/entity/sheep/sheep_fur.png");

    protected AnimalModelAdapter<SheepEntity> wool;

    public SheepModelAdapter(SheepEntity entity) throws IllegalArgumentException {
        super(entity, TEXTURE);

    }
    
    @Override
    protected MultipartReplayModel captureBaseModel(AnimalModel<SheepEntity> model) {
        return super.captureBaseModel(model);
    }
}
