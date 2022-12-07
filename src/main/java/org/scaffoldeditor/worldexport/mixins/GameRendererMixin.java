package org.scaffoldeditor.worldexport.mixins;

import org.scaffoldeditor.worldexport.replaymod.util.FovProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;

@Mixin(GameRenderer.class)
public class GameRendererMixin {    

    @Shadow @Final
    private MinecraftClient client;
    
    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    void overrideFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> ci) {
        Entity entity = this.client.getCameraEntity() == null ? this.client.player : this.client.getCameraEntity();
        if (!camera.isThirdPerson() && entity instanceof FovProvider) {
            ci.setReturnValue(((FovProvider) entity).getFov());
        }
    }
}
