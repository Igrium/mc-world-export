package com.igrium.worldexport.debugger;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record CurveSelectionReference(String entityName, String modelPartName, int curveIndex, int channelIndex) {
    @Nullable
    public AnimationCurve getCurve(Map<? super String, ? extends CapturedEntity> entityMap) {
        CapturedEntity entity = entityMap.get(entityName);
        if (entity == null)
            return null;

        List<AnimationCurve> curves = entity.getCurves().get(modelPartName);
        if (curves == null || curveIndex >= curves.size())
            return null;

        return curves.get(curveIndex);
    }

    public static boolean isEntitySelected(Iterable<? extends CurveSelectionReference> curves, String entityName) {
        for (var curve : curves) {
            if (curve.entityName.equals(entityName))
                return true;
        }
        return false;
    }

    public static boolean isModelPartSelected(Iterable<? extends CurveSelectionReference> curves, String entityName, String modelPartName) {
        for (var curve : curves) {
            if (curve.entityName.equals(entityName)
                    && curve.modelPartName.equals(modelPartName))
                return true;
        }
        return false;
    }

    public static boolean isCurveIndexSelected(Iterable<? extends CurveSelectionReference> curves, String entityName, String modelPartName, int curveIndex) {
        for (var curve : curves) {
            if (curve.entityName.equals(entityName)
                    && curve.modelPartName.equals(modelPartName)
                    && curve.curveIndex == curveIndex)
                return true;
        }
        return false;
    }
}

