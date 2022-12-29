package org.scaffoldeditor.worldexport.util;

import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.WritableColor;

import net.minecraft.util.math.ColorHelper;

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

    /**
     * Convert a <code>ReadableColor</code> to an ARGB int.
     * @param color The color.
     * @return The ARGB int.
     */
    public static int colorToARGB(ReadableColor color) {
        return ColorHelper.Argb.getArgb(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Convert an ARGB int to a color object.
     * @param <T> The color type.
     * @param argb The ARGB int.
     * @param dest The color to write into.
     * @return <code>dest</code>
     */
    public static <T extends WritableColor> T argbToColor(int argb, T dest) {
        dest.set(argb >> 16 & 0xFF,
                argb >> 8 & 0xFF,
                argb & 0xFF,
                argb >>> 24);
        return dest;
    }

    /**
     * Convert hue/saturation/color values into RGB color values and put them into a
     * <code>WritableColor</code>. Values range from <code>0..1</code>
     * 
     * @param <T>        The color type.
     * @param hue        Hue.
     * @param saturation Saturation.
     * @param value      Value.
     * @param dest       The color object to write into.
     * @return <code>dest</code>
     */
    public static <T extends WritableColor> T hsvToColor(float hue, float saturation, float value, T dest) {
        float chroma = value * saturation;
        float huePrime = hue / 60.0f;
        float intermediate = chroma * (1 - Math.abs(huePrime % 2 - 1));

        float red1, green1, blue1;
        if (huePrime >= 0 && huePrime < 1) {
            red1 = chroma;
            green1 = intermediate;
            blue1 = 0;
        } else if (huePrime >= 1 && huePrime < 2) {
            red1 = intermediate;
            green1 = chroma;
            blue1 = 0;
        } else if (huePrime >= 2 && huePrime < 3) {
            red1 = 0;
            green1 = chroma;
            blue1 = intermediate;
        } else if (huePrime >= 3 && huePrime < 4) {
            red1 = 0;
            green1 = intermediate;
            blue1 = chroma;
        } else if (huePrime >= 4 && huePrime < 5) {
            red1 = intermediate;
            green1 = 0;
            blue1 = chroma;
        } else if (huePrime >= 5 && huePrime < 6) {
            red1 = chroma;
            green1 = 0;
            blue1 = intermediate;
        } else {
            red1 = 0;
            green1 = 0;
            blue1 = 0;
        }

        float valueMinusChroma = value - chroma;
        float red = red1 + valueMinusChroma;
        float green = green1 + valueMinusChroma;
        float blue = blue1 + valueMinusChroma;
        
        dest.setRed((byte) (red * 255));
        dest.setGreen((byte) (green * 255));
        dest.setBlue((byte) (blue * 255));

        return dest;
    }
}
