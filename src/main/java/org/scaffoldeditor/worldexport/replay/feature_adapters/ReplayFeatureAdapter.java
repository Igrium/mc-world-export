package org.scaffoldeditor.worldexport.replay.feature_adapters;

import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

public interface ReplayFeatureAdapter<T> {
    void writePose(Pose<T> pose, float tickDelta);
    void generateMaterials(MaterialConsumer consumer);
}
