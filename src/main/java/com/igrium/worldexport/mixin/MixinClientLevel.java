package com.igrium.worldexport.mixin;

import com.igrium.worldexport.event.ClientBlockUpdatedEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
@Environment(EnvType.CLIENT)
public abstract class MixinClientLevel {
    @Inject(method= "sendBlockUpdated", at = @At("RETURN"))
    public void updateListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        ClientBlockUpdatedEvent.EVENT.invoker().onBlockUpdated(pos, oldState, newState, (Level)(Object) this);
    }
}
