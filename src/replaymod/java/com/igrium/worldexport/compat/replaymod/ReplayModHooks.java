package com.igrium.worldexport.compat.replaymod;

import com.replaymod.core.ReplayMod;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ReplayModHooks {
    private ReplayModHooks() {}

    /**
     * Completed once the Replay Mod has finished initializing.
     */
    @Getter
    private final static CompletableFuture<ReplayMod> onInit = new CompletableFuture<>();

    /**
     * Run a piece of code directly after the Replay Mod has initialized.
     * If the replay mod is already loaded, run the code immediately.
     *
     * @param r The code to run.
     */
    public static void onInit(Consumer<? super ReplayMod> r) {
        onInit.thenAccept(r);
    }
}
