package org.scaffoldeditor.worldexport.replaymod;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.replaymod.core.ReplayMod;

public final class ReplayModHooks {
    private ReplayModHooks() {};
    private static CompletableFuture<ReplayMod> future = new CompletableFuture<>();

    /**
     * Return a <code>CompletableFuture</code> that completes once the Replay Mod
     * has finished initializing.
     * 
     * @return The future.
     */
    public static CompletableFuture<ReplayMod> waitForInit() {
        return future;
    }

    /**
     * Run a piece of code directly after the Replay Mod has initialized. If the
     * replay mod is already loaded, run the code immedietly.
     * 
     * @param r The code to run.
     */
    public static void onReplayModInit(Consumer<ReplayMod> r) {
        future.thenAccept(r);
    }
}
