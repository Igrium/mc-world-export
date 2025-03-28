package org.scaffoldeditor.worldexport.mixins;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ElytraEntityModel.class)
public interface ElytraEntityModelAccessor {
    @Accessor("rightWing")
    public ModelPart getRightWing();
    @Accessor("leftWing")
    public ModelPart getLeftWing();
}
