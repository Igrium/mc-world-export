package org.scaffoldeditor.worldexport;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.block.BlockState;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public class WorldExportMod implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("worldexport");
    private static WorldExportMod instance;

    public static WorldExportMod getInstance() {
        return instance;
    }

    private Set<BiConsumer<BlockPos, BlockState>> blockUpdateListeners = new HashSet<>();
    
    public void onBlockUpdated(BiConsumer<BlockPos, BlockState> listener) {
        blockUpdateListeners.add(listener);
    }

    public boolean removeOnBlockUpdated(BiConsumer<BlockPos, BlockState> listener) {
        return blockUpdateListeners.remove(listener);
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        ExportCommand.register();

        ClientBlockPlaceCallback.EVENT.register((pos, state) -> {
            blockUpdateListeners.forEach(listener -> listener.accept(pos, state));
            return ActionResult.PASS;
        });
    }
    
}
