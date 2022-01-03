package org.scaffoldeditor.worldexport.mixins;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.model.ModelPart;

@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    
    @Accessor("children")
    Map<String, ModelPart> getChildren();
    
}
