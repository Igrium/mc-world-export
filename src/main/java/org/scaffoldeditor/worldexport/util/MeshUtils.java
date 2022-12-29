package org.scaffoldeditor.worldexport.util;

import javax.annotation.Nullable;

import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.scaffoldeditor.worldexport.vcap.ObjVertexConsumer;
import org.scaffoldeditor.worldexport.vcap.WrappedVertexConsumerProvider;

import de.javagl.obj.Obj;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPart.Cuboid;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public final class MeshUtils {
    private MeshUtils() {};

    public static final Matrix4dc NEUTRAL_TRANSFORM = new Matrix4d();

    /**
     * A Minecraft matrix stack entry representing an identity matrix.
     */
    public static final MatrixStack.Entry IDENTITY_ENTRY = new MatrixStack().peek();

    public static void appendCuboid(Cuboid cuboid, Obj target, Matrix4dc transform) {
        ObjVertexConsumer consumer = new ObjVertexConsumer(target, transform);
        cuboid.renderCuboid(IDENTITY_ENTRY, consumer, 255, 0, 255, 255, 255, 255);
    }
    
    /**
     * Append a Minecraft model part (including all cuboids) to an obj.
     * 
     * @param part      Model part to append.
     * @param target    Obj to add to.
     * @param mask      Mask the operation to only <i>this</code> model part. If
     *                  false, children will be included as well.
     * @param transform Transform to apply to the appended vertices.
     */
    public static void appendModelPart(ModelPart part, Obj target, boolean mask, @Nullable Matrix4dc transform) {
        if (transform == null) transform = NEUTRAL_TRANSFORM;
        ObjVertexConsumer consumer = new ObjVertexConsumer(target, transform);

        part.forEachCuboid(new MatrixStack(), (matrix, path, index, cuboid) -> {
            if (mask && !path.equals("")) return;
            cuboid.renderCuboid(IDENTITY_ENTRY, consumer, 255, 0, 255, 255, 255, 255);
        });
    }

    /**
     * Create a vertex consumer provider that always provides this vertex consumer (not suitable for rendering)
     * @param consumer The vertex consumer to use.
     * @return The generated vertex comsumer provider.
     * @deprecated Use {@link WrappedVertexConsumerProvider} instead.
     */
    @Deprecated
    public static VertexConsumerProvider wrapVertexConsumer(VertexConsumer consumer) {
        return new WrappedVertexConsumerProvider(consumer);
    }
}
