package org.scaffoldeditor.worldexport.replaymod;

import com.replaymod.render.RenderSettings;
import com.replaymod.render.RenderSettings.RenderMethod;

public final class RenderSettingsUtils {
    private RenderSettingsUtils() {};

    public static RenderSettings withRenderMethod(RenderSettings settings, RenderMethod method) {
        return new RenderSettings(method,
                settings.getEncodingPreset(),
                settings.getVideoWidth(),
                settings.getVideoHeight(),
                settings.getFramesPerSecond(),
                settings.getBitRate(),
                settings.getOutputFile(),
                settings.isRenderNameTags(),
                settings.isIncludeAlphaChannel(),
                settings.isStabilizeYaw(),
                settings.isStabilizePitch(),
                settings.isStabilizeRoll(),
                settings.getChromaKeyingColor(),
                settings.getSphericalFovX(),
                settings.getSphericalFovY(),
                settings.isInjectSphericalMetadata(),
                settings.isDepthMap(),
                settings.isCameraPathExport(),
                settings.getAntiAliasing(),
                settings.getExportCommand(),
                settings.getExportArguments(),
                settings.isHighPerformance());
    }
}
