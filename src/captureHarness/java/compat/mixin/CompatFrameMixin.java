package compat.mixin;
import compat.CompatHarness;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class CompatFrameMixin {
    @Inject(method = "runTick", at = @At("RETURN"), remap = false)
    private void compat$frame(boolean tick, CallbackInfo ci) {
        CompatHarness.frame((Minecraft) (Object) this);
    }
}
