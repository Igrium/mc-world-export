package com.igrium.replay_debugger.mixins;

import java.awt.HeadlessException;

import com.igrium.replay_debugger.ReplayDebugger;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    protected void init(CallbackInfo ci) {
        // addDrawableChild(new ButtonWidget(width - 98, 0, 98, 20, Text.of("Debug Replays"), (button) -> {
        //     try {
        //         ReplayDebugger instance = new ReplayDebugger();
        //         instance.launch();
        //     } catch (HeadlessException e) {
        //         LogManager.getLogger(ReplayDebugger.class).error("Unable to launch debugger in headless environment.");
        //     }
            
		// }));
    }
    
}
