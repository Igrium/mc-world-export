package org.scaffoldeditor.worldexport.replay.model_adapters.specific;

import java.util.Map;

import org.scaffoldeditor.worldexport.replay.model_adapters.AnimalModelAdapter;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class ChickenModelAdapter extends AnimalModelAdapter<ChickenEntity> {

    private static final Identifier TEXTURE = new Identifier("textures/entity/chicken.png");

    public ChickenModelAdapter(ChickenEntity entity)
            throws IllegalArgumentException {
        super(entity, TEXTURE);
    }
    
    @Override
    protected float getAnimationProgress(float tickDelta) {
        float flapProgress = MathHelper.lerp(tickDelta, getEntity().prevFlapProgress, getEntity().flapProgress);
        float maxDeviation = MathHelper.lerp(tickDelta, getEntity().prevMaxWingDeviation, getEntity().maxWingDeviation);
        return (MathHelper.sin(flapProgress) + 1) * maxDeviation;
    }
    
    @Override
    protected void extractPartNames(AnimalModel<ChickenEntity> model, Map<ModelPart, String> partNames) {
        super.extractPartNames(model, partNames);
        

        // if(model instanceof ChickenEntityModel){
        //     ChickenEntityModel<ChickenEntity> chicken = (ChickenEntityModel<ChickenEntity>) model;
        //     partNames.put(chicken.beak, EntityModelPartNames.BEAK);
        //     partNames.put(chicken.head, EntityModelPartNames.HEAD);
        //     partNames.put(chicken.leftLeg, EntityModelPartNames.LEFT_LEG);
        //     partNames.put(chicken.leftWing, EntityModelPartNames.LEFT_WING);
        //     partNames.put(chicken.rightLeg, EntityModelPartNames.RIGHT_LEG);
        //     partNames.put(chicken.rightWing, EntityModelPartNames.RIGHT_WING);
        //     partNames.put(chicken.torso, "torso");
        //     partNames.put(chicken.wattle, EntityModelPartNames.WATTLE);
        //   }
    }
}
