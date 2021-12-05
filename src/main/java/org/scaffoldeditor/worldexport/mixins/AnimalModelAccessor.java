package org.scaffoldeditor.worldexport.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;

@Mixin(AnimalModel.class)
public interface AnimalModelAccessor {

    @Invoker("getHeadParts")
    public Iterable<ModelPart> retrieveHeadParts();
    
    @Invoker("getBodyParts")
    public Iterable<ModelPart> retrieveBodyParts();
}
