package org.scaffoldeditor.worldexport.replay.model_adapters;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.scaffoldeditor.worldexport.mixins.AnimalModelAccessor;
import org.scaffoldeditor.worldexport.mixins.QuadrupedModelAccessor;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.render.entity.model.QuadrupedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

/**
 * Umbrella model adapter that works specifically with entities which use Animal Models.
 */
public class AnimalModelAdapter<T extends LivingEntity> extends LivingEntityModelAdapter<T, AnimalModel<T>> {

    private Identifier texture;

    public AnimalModelAdapter(T entity, Identifier texture) throws IllegalArgumentException {
        super(entity);
        this.texture = texture;
    }

    @Override
    public Identifier getTexture() {
        return texture;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AnimalModel<T> extractModel(LivingEntityRenderer<? super T, ?> entityRenderer) throws ClassCastException {
        return (AnimalModel<T>) entityRenderer.getModel();
    }

    @Override
    protected void extractPartNames(AnimalModel<T> model, Map<ModelPart, String> partNames) {
        if (model instanceof QuadrupedEntityModel) {
            QuadrupedModelAccessor accessor = (QuadrupedModelAccessor) model;
            partNames.put(accessor.getHead(), EntityModelPartNames.HEAD);
            partNames.put(accessor.getBody(), EntityModelPartNames.BODY);
            partNames.put(accessor.getRightHindLeg(), EntityModelPartNames.RIGHT_HIND_LEG);
            partNames.put(accessor.getLeftHindLeg(), EntityModelPartNames.LEFT_HIND_LEG);
            partNames.put(accessor.getRightFrontLeg(), EntityModelPartNames.RIGHT_FRONT_LEG);
            partNames.put(accessor.getLeftFrontLeg(), EntityModelPartNames.LEFT_FRONT_LEG);
        }

        if (model instanceof BipedEntityModel) {
            BipedEntityModel<T> biped = (BipedEntityModel<T>) model;
            partNames.put(biped.head, EntityModelPartNames.HEAD);
            partNames.put(biped.hat, EntityModelPartNames.HAT);
            partNames.put(biped.body, EntityModelPartNames.BODY);
            partNames.put(biped.rightArm, EntityModelPartNames.RIGHT_ARM);
            partNames.put(biped.leftArm, EntityModelPartNames.LEFT_ARM);
            partNames.put(biped.rightLeg, EntityModelPartNames.RIGHT_LEG);
            partNames.put(biped.leftLeg, EntityModelPartNames.LEFT_LEG);
        }
    }


    @Override
    protected Iterable<Pair<String, ModelPart>> getRootParts() {
        return this::createRootPartIterator;
    }

    private Iterator<Pair<String, ModelPart>> createRootPartIterator() {
        Stream<Pair<String, ModelPart>> stream1 = StreamSupport
                .stream(((AnimalModelAccessor) model).retrieveBodyParts().spliterator(), false)
                .map(part -> new Pair<>(findName(part), part));

        Stream<Pair<String, ModelPart>> stream2 = StreamSupport
                .stream(((AnimalModelAccessor) model).retrieveHeadParts().spliterator(), false)
                .map(part -> new Pair<>(findName(part), part));
        
        return Stream.concat(stream1, stream2).iterator();
    }

    private String findName(ModelPart part) {
        return boneMapping.containsKey(part) ? boneMapping.get(part).getName() : part.toString();
    }

}
