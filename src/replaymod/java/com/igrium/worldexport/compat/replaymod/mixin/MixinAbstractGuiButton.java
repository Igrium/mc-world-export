package com.igrium.worldexport.compat.replaymod.mixin;

import com.igrium.worldexport.compat.replaymod.util.LabelColorProvider;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Dumb hack to allow callbacks to override a button's label color at render-time.
 */
@Mixin(AbstractGuiButton.class)
public class MixinAbstractGuiButton {
    @ModifyVariable(method = "draw", at = @At(value = "INVOKE", target = "Lcom/replaymod/lib/de/johni0702/minecraft" +
            "/gui/element/AbstractGuiButton;isMouseHovering" +
            "(Lcom/replaymod/lib/de/johni0702/minecraft/gui/utils/lwjgl/ReadablePoint;)Z"), name = "color")
    int replaceColor(int color) {
        if (this instanceof LabelColorProvider prov) {
            return prov.getLabelColor(color);
        } else {
            return color;
        }
    }
}
