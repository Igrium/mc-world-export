package org.scaffoldeditor.worldexport.replaymod.util;

/**
 * An entity that can provide a roll value for the camera.
 */
public interface RollProvider {
    /**
     * Get the current roll.
     * @return The roll of this camera in degrees.
     */
    public float getRoll();
}
