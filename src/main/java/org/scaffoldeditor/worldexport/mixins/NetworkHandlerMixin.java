package org.scaffoldeditor.worldexport.mixins;

import org.scaffoldeditor.worldexport.ClientBlockPlaceCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;

@Mixin(ClientPlayNetworkHandler.class)
public class NetworkHandlerMixin {
    
    @Inject(method = "onBlockUpdate", at = @At("RETURN"))
    private void onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo info) {
        ClientBlockPlaceCallback.EVENT.invoker().place(packet.getPos(), packet.getState());
    }
}
