package org.scaffoldeditor.worldexport.replay;

import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.minecraft.util.Identifier;

public interface BaseReplayEntity {
    void generateMaterials(MaterialConsumer file);
    ReplayModel<?> getModel();
    String getName();
    float getStartTime();
    
    /**
     * Get the ID of the Minecraft entity this represents.
     * @return Minecraft ID.
     */
    Identifier getMinecraftID();

    /**
     * Get the FPS of this entity's animation.
     * @return Anim FPS.
     */
    float getFPS();

    Iterable<Pose<?>> getFrames();
}
