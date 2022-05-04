package org.scaffoldeditor.worldexport.util;

import org.joml.Matrix4dc;
import org.scaffoldeditor.worldexport.vcap.ObjVertexConsumer;

import de.javagl.obj.Obj;
import net.minecraft.client.model.ModelPart.Cuboid;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public final class MeshUtils {
    private MeshUtils() {};

    /**
     * A Minecraft matrix stack entry representing an identity matrix.
     */
    public static final MatrixStack.Entry IDENTITY_ENTRY = new MatrixStack().peek();

    public static void appendCuboid(Cuboid cuboid, Obj target, Matrix4dc transform) {
        ObjVertexConsumer consumer = new ObjVertexConsumer(target, transform);
        cuboid.renderCuboid(IDENTITY_ENTRY, consumer, 255, 0, 255, 255, 255, 255);
    }

    /**
     * Create a vertex consumer provider that always provides this vertex consumer (not suitable for rendering)
     * @param consumer The vertex consumer to use.
     * @return The generated vertex comsumer provider.
     */
    public static VertexConsumerProvider wrapVertexConsumer(VertexConsumer consumer) {
        return new VertexConsumerProvider() {

            @Override
            public VertexConsumer getBuffer(RenderLayer layer) {
                return consumer;
            }
            
        };
    }
}
