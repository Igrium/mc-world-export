package com.igrium.worldexport.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(ModelPart.class)
public interface AccessorModelPart {
    @Accessor("children")
    Map<String, ModelPart> getChildren();

    @Accessor("cubes")
    List<ModelPart.Cube> getCubes();
}
