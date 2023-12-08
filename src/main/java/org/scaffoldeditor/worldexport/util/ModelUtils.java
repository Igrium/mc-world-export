package org.scaffoldeditor.worldexport.util;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;

public class ModelUtils {
    public static MatrixStack getPartTransform(ModelPart part, MatrixStack dest) {
        dest.translate(part.pivotX / 16f, part.pivotY / 16, part.pivotZ / 16f);
        dest.multiply(new Quaternionf().rotateZYX(part.roll, part.yaw, part.pitch));
        dest.scale(part.xScale, part.yScale, part.zScale);
        return dest;
    }

    public static Matrix4f getPartTransform(ModelPart part, Matrix4f dest) {
        dest.translate(part.pivotX / 16f, part.pivotY / 16f, part.pivotZ / 16f);
        dest.rotate(new Quaternionf().rotateZYX(part.roll, part.yaw, part.pitch));
        dest.scale(part.xScale, part.yScale, part.zScale);
        return dest;
    }

    public static Matrix4d getPartTransform(ModelPart part, Matrix4d dest) {
        dest.translate(part.pivotX / 16d, part.pivotY / 16d, part.pivotZ / 16d);
        dest.rotate(new Quaterniond().rotateZYX(part.roll, part.yaw, part.pitch));
        dest.scale(part.xScale, part.yScale, part.zScale);
        return dest;
    }
}
