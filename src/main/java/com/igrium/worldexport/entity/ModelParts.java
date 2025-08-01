package com.igrium.worldexport.entity;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.mesh.MeshUtils;
import com.igrium.worldexport.mesh.ObjVertexConsumer;
import com.igrium.worldexport.mixin.AccessorModelPart;
import de.javagl.obj.Obj;
import net.minecraft.client.model.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class ModelParts {

    /**
     * Capture the complete pose of a model part and its children.
     *
     * @param part      Root part to capture.
     * @param rootName  Name of the root part.
     * @param format    Curve format to use.
     * @param capture   Entity capture to add frame to.
     * @param tick      Tick index in the capture.
     * @param recursive If true, also capture the part's children.
     * @apiNote Child transforms are captured relative to the immediate parent; <em>not</em> the root.
     * This works under the assumption that the child parts will be parented in the replay file.
     */
    public static void captureModelPose(ModelPart part, String rootName, AnimationCurve.CurveFormat format,
                                        CapturedEntity capture, int tick, boolean recursive) {
        Vector3f pos = getPosition(part, new Vector3f());
        Quaternionf rot = format.hasRotation() ? getRotation(part, new Quaternionf()) : null;
        Vector3f scale = format.hasScale() ? getScale(part, new Vector3f()) : null;

        capture.addFrame(rootName, tick, format, pos, rot, scale);

        if (recursive) {
            for (var childEntry : getChildren(part).entrySet()) {
                String childPath = rootName + "/" + childEntry.getKey();
                captureModelPose(childEntry.getValue(), childPath, format, capture, tick, true);
            }
        }
    }

    /**
     * Identify all parent-child relations in this part's children.
     *
     * @param part             Root part to scan.
     * @param rootName         Name of the root part.
     * @param relationConsumer A consumer that accepts a child part and its parent.
     */
    public static void buildParentHierarchy(ModelPart part, String rootName, BiConsumer<String, String> relationConsumer) {
        for (var childEntry : getChildren(part).entrySet()) {
            String childPath = rootName + "/" + childEntry.getKey();
            relationConsumer.accept(childPath, rootName);
            buildParentHierarchy(childEntry.getValue(), childPath, relationConsumer);
        }
    }

    /**
     * Iterate over a model part and all its children.
     *
     * @param part         Root part.
     * @param rootName     Name of the root part.
     * @param partConsumer A consumer that accepts child part path and object.
     * @apiNote The parent part <em>is</em> included in the enumeration.
     */
    public static void forEachPart(ModelPart part, String rootName, BiConsumer<String, ModelPart> partConsumer) {
        forEachPart(part, rootName, partConsumer, p -> true);
    }

    /**
     * Iterate over a model part and all its children.
     *
     * @param part         Root part.
     * @param rootName     Name of the root part.
     * @param partConsumer A consumer that accepts child part path and object.
     * @param predicate    A predicate to test whether iteration should continue over a given part.
     *                     The first part to return false will have itself and its children excluded from iteration.
     * @apiNote The parent part <em>is</em> included in the enumeration.
     */
    public static void forEachPart(ModelPart part, String rootName, BiConsumer<String, ModelPart> partConsumer, Predicate<ModelPart> predicate) {
        if (!predicate.test(part))
            return;

        partConsumer.accept(rootName, part);
        for (var childEntry : getChildren(part).entrySet()) {
            String childPath = rootName + "/" + childEntry.getKey();
            forEachPart(childEntry.getValue(), childPath, partConsumer, predicate);
        }
    }

    public static Vector3f getPosition(ModelPart part, Vector3f dest) {
        return dest.set(part.pivotX / 16, part.pivotY / 16, part.pivotZ / 16);
    }

    public static Quaternionf getRotation(ModelPart part, Quaternionf dest) {
        return dest.rotationZYX(part.roll, part.yaw, part.pitch);
    }

    public static Vector3f getScale(ModelPart part, Vector3f dest) {
        return dest.set(part.xScale, part.yScale, part.zScale);
    }

    /**
     * Render a model part into an OBJ mesh.
     *
     * @param modelPart  Model part to render.
     * @param targetMesh Mesh to render into.
     * @return <code>targetMesh</code>
     */
    public static Obj modelPartToMesh(ModelPart modelPart, Obj targetMesh) {
        ObjVertexConsumer vertexConsumer = new ObjVertexConsumer(targetMesh);
        vertexConsumer.setEnableColors(false);
        vertexConsumer.setEnableNormals(false);

        for (var cuboid : getCuboids(modelPart)) {
            cuboid.renderCuboid(MeshUtils.IDENTITY_ENTRY, vertexConsumer, 255, 0, Integer.MAX_VALUE);
        }
        vertexConsumer.pushFace();
        return targetMesh;
    }

    // Shortcut methods to reduce typing
    private static Map<String, ModelPart> getChildren(ModelPart part) {
        return ((AccessorModelPart)(Object) part).getChildren();
    }

    private static List<ModelPart.Cuboid> getCuboids(ModelPart part) {
        return ((AccessorModelPart)(Object) part).getCuboids();
    }
}
