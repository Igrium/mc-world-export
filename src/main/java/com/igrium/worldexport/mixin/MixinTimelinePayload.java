package com.igrium.worldexport.mixin;
import com.igrium.worldexport.timeline.TimelineCapture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric 6.3.x dispatches receive on the client thread, including replayed custom packets. */
@Mixin(targets="net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon",remap=false)
public class MixinTimelinePayload {
    @Inject(method="receive",at=@At("HEAD"),remap=false)
    private void worldexport$payload(ClientPlayNetworking.PlayPayloadHandler<?> handler,CustomPacketPayload payload,CallbackInfo ci){
        TimelineCapture.payloadReceived(payload);
    }
}
