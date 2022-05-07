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
        Quaterniond diff = src.difference(target, new Quaterniond());
        boolean compatible = Math.abs(diff.angle()) <= Math.PI;

        if (compatible) {
            dest.set(src);
        } else {
            dest.set(-src.x(), -src.y(), -src.z(), -src.w());
        }
        
        return dest;
    }

    /**
     * Given any rotation, there are two possible quaternions that can represent
     * that rotation. This function ensures that two quaternions are "compatible",
     * meaning they use similar numbers that won't spaz out when interpolated.
     * 
     * @param src    Quaternion to check the compatibility of.
     * @param target Quaternion to check <code>src</code>'s compatibility with.
     * @param dest   Quaternion to store the result: a modified version of
     *               <code>src</code> that is compatible with <code>target</code>.
     * @return <code>dest</code>
     */
    public static Quaterniond makeQuatsCompatible(Quaterniondc src, Quaterniondc target, Quaterniond dest) {
        return makeQuatsCompatible(src, target, .25, dest);
    }
}
