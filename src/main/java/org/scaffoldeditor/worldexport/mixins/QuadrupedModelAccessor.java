package org.scaffoldeditor.worldexport.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.QuadrupedEntityModel;

@Mixin(QuadrupedEntityModel.class)
public interface QuadrupedModelAccessor {

    @Accessor("head")
    ModelPart getHead();

    @Accessor("body")
    ModelPart getBody();

    @Accessor("rightHindLeg")
    ModelPart getRightHindLeg();

    @Accessor("leftHindLeg")
    ModelPart getLeftHindLeg();

    @Accessor("rightFrontLeg")
    ModelPart getRightFrontLeg();

    @Accessor("leftFrontLeg")
    ModelPart getLeftFrontLeg();
}
