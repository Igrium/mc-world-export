package org.scaffoldeditor.worldexport.util;

public final class RenderUtils {
    private RenderUtils() {};

    /**
     * Takes an integer-based color value and makes the alpha channel {@code 255},
     * essentially stripping it of all transparency.
     * 
     * @param color The input color.
     * @return The color without any transparency.
     */
    public static int stripAlpha(int color) {
        // Thanks to ChatGPT for this snippet!
        return (color & 0x00FFFFFF) | 0xFF000000;
    }
}
