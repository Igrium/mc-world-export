package org.scaffoldeditor.worldexport.util;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;

public final class MathUtils {
    private MathUtils() {}
  
    /**
     * Given any rotation, there are two possible quaternions that can represent
     * that rotation. This function ensures that two quaternions are "compatible",
     * meaning they use similar numbers that won't spaz out when interpolated.
     * 
     * @param src    Quaternion to check the compatibility of.
     * @param target Quaternion to check <code>src</code>'s compatibility with.
     * @param margin Margin of difference quats are allowed to have between each
     *               other. Quaternions with differences in values greater than this
     *               will be treated as distinct. Recommended value: ~.25
     * @param dest   Quaternion to store the result: a modified version of
     *               <code>src</code> that is compatible with <code>target</code>.
     * @return <code>dest</code>
     */
    public static Quaterniond makeQuatsCompatible(Quaterniondc src, Quaterniondc target, double margin, Quaterniond dest) {
        boolean similar = absWithinRange(src.w(), target.w(), margin)
                && absWithinRange(src.x(), target.x(), margin)
                && absWithinRange(src.y(), target.y(), margin)
                && absWithinRange(src.z(), target.z(), margin);
        
        if (!similar) {
            dest.set(src);
            return dest;
        }

        // Ensure all values are on the same side of zero.
        boolean compatible = ((src.w() >= 0 && target.w() >= 0) || (src.w() < 0 && target.w() < 0))
                && ((src.x() >= 0 && target.x() >= 0) || (src.x() < 0 && target.x() < 0))
                && ((src.y() >= 0 && target.y() >= 0) || (src.y() < 0 && target.y() < 0))
                && ((src.z() >= 0 && target.z() >= 0) || (src.z() < 0 && target.z() < 0));

        if (compatible) {
            dest.set(src);
        } else {
            dest.set(-src.x(), -src.y(), -src.z(), -src.w());
        }
        
        return dest;
    }

    private static boolean absWithinRange(double a, double b, double margin) {
        return Math.abs(Math.abs(a) - Math.abs(b)) <= margin;
    }
}
