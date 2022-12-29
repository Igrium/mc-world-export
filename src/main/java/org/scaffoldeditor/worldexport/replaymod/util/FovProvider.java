package org.scaffoldeditor.worldexport.replaymod.util;

/**
 * If the current camera entity implements this interface, the game's FOV is
 * replaced by the return value of <code>getFov()</code>
 */
public interface FovProvider {

    /**
     * Get the field of view to use.
     * @return The FOV in degrees.
     */
    public double getFov();
}
