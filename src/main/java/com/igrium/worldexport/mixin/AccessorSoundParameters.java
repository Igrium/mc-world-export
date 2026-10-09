package com.igrium.worldexport.mixin;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AbstractSoundInstance.class)
public interface AccessorSoundParameters {
    @Accessor("volume") float worldexport$getRequestedVolume();
    @Accessor("pitch") float worldexport$getRequestedPitch();
}
