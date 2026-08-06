package com.igrium.worldexport.mixin_helper;

public class ReplayModsMixinPlugin extends ModCondMixinPlugin {
    @Override
    protected String targetModId() {
        return "replaymod";
    }
}
