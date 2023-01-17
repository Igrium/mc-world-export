package org.scaffoldeditor.worldexport.mixins;

import org.scaffoldeditor.worldexport.ClientBlockPlaceCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(ClientWorld.class)
@Environment(EnvType.CLIENT)
public abstract class ClientWorldMixin {

    @Inject(method = "updateListeners", at = @At("RETURN"))
    public void updateListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        ClientBlockPlaceCallback.EVENT.invoker().place(pos, oldState, newState, (World)(Object) this);
    }
}
