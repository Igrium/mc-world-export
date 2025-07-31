package com.igrium.worldexport.mesh;

import lombok.Getter;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.Map;

/**
 * A vertex consumer wrapper that ensures that a given face cannot be output twice.
 * Significantly higher memory overhead that FaceVertexConsumer, so should be used sparingly.
 *
 * @implNote <code>consumer.handleFace</code> is called directly, so offsets in the base consumer won't apply.
 */
public class DuplicateCheckingVertexConsumer extends FaceVertexConsumer {


    private record QuadVerts(Vector3fc a, Vector3fc b, Vector3fc c, Vector3fc d) {
        static QuadVerts fromArray(Vector3fc[] array) {
            return new QuadVerts(new Vector3f(array[0]), new Vector3f(array[1]),
                    new Vector3f(array[2]), new Vector3f(array[3]));
        }
    }

    @Getter
    private final FaceVertexConsumer consumer;

    private final Map<QuadVerts, Boolean> faces = new HashMap<>();

    public DuplicateCheckingVertexConsumer(FaceVertexConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    protected void handleFace(Vector3fc[] vertices, Vector3fc[] colors, Vector3fc[] normals, Vector2fc[] texCoords) {
        QuadVerts face = QuadVerts.fromArray(vertices);
        if (faces.putIfAbsent(face, true) == null) {
            consumer.handleFace(vertices, colors, normals, texCoords);
        }
    }
}
