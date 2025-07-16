package com.igrium.worldexport.entity;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.mesh.MeshUtils;
import de.javagl.obj.*;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

import java.io.*;
import java.util.*;

/**
 * Holds an entity's exported model and animation data.
 */
public class CapturedEntity {

    /**
     * The name of the animation entry that's used for the root.
     */
    public static final String ROOT_NAME = "root";

    /**
     * A map of model part names and their mesh data.
     * @apiNote It's possible for a model part to be present in the animation, but not have an entry here.
     */
    @Getter
    private final Map<String, ReadableObj> modelParts = new HashMap<>();

    /**
     * A map of all animation curves and the part names they belong to.
     */
    @Getter
    private final Map<String, List<AnimationCurve>> curves = new HashMap<>();

    /**
     * If a model part needs to be parented to another,
     * it goes here with the child as the key and the parent as the value.
     * <p>
     * If a part is not in this map, it is assumed parented to the root.
     *
     * @apiNote Parts can only have one parent, which is set for the duration of the animation.
     * All transforms on that part are relative to the parent.
     */
    @Getter
    private final Map<String, String> parents = new HashMap<>();

    /**
     * Attempt to find an animation curve that contains (or can contain) the selected tick.
     * <p>
     * If a curve is found where the tick is already within its, range, return that.
     * Otherwise, attempt to find a curve that can accommodate the tick while growing by one.
     *
     * @param curves Animations to search through.
     * @param tick   Tick to search with
     * @return The curve, or <code>null</code> if no applicable curve was found.
     */
    public static @Nullable AnimationCurve getCurve(Collection<? extends AnimationCurve> curves, int tick) {
        // If no curve is found that already contains the tick, keep track of one that will if it grows by 1.
        AnimationCurve extendableCurve = null;
        for (var curve : curves){
            int lastTickExclusive = curve.getFrameOffset() + curve.size();

            if (tick == lastTickExclusive) {
                extendableCurve = curve;
            } else if (curve.getFrameOffset() <= tick && tick < lastTickExclusive) {
                return curve;
            }
        }
        return extendableCurve;
    }

    /**
     * Get or create a curve that's able to accept a keyframe at a given tick.
     * <p>
     * Searches for curves where the target frame is in range or one tick out of range.
     * If none are found, creates a new one with the tick as its frame offset.
     *
     * @param modelPart Model part to search in.
     * @param tick      The tick in question.
     * @return The new or existing curve.
     */
    public AnimationCurve getOrCreateCurve(String modelPart, int tick) {
        List<AnimationCurve> curveList = curves.computeIfAbsent(modelPart, p -> new ArrayList<>());
        AnimationCurve curve = getCurve(curveList, tick);
        if (curve == null) {
            curve = new AnimationCurve();
            curve.setFrameOffset(tick);
            curveList.add(curve);
        }
        return curve;
    }

    public void addFrame(String modelPart, int tick, Vector3fc pos, Quaternionfc rot, Vector3fc scale) {
        AnimationCurve curve = getOrCreateCurve(modelPart, tick);
        int index = tick - curve.getFrameOffset();
        if (index >= curve.size()) {
            curve.addFrame(pos, rot, scale);
        } else {
            curve.setFrame(index, pos, rot, scale);
        }
    }

    public void addFrame(String modelPart, int tick, Matrix4fc transform) {
        AnimationCurve curve = getOrCreateCurve(modelPart, tick);
        int index = tick - curve.getFrameOffset();
        if (index >= curve.size()) {
            curve.addFrame(transform);
        } else {
            curve.setFrame(index, transform);
        }
    }

    public void writeAnimFile(DataOutputStream out) throws IOException {
        int total = 0;
        for (var list : curves.values()) {
            total += list.size();
        }
        out.writeInt(total);
        for (var entry : curves.entrySet()) {
            for (var curve : entry.getValue()) {
                out.writeUTF(entry.getKey());
                curve.write(out);
            }
        }
    }

    public int readAnimFile(DataInputStream in) throws IOException {
        int size = in.readInt();
        int i;
        for (i = 0; i < size; i++) {
            String name = in.readUTF();
            AnimationCurve curve = new AnimationCurve();
            curve.read(in);
            curves.computeIfAbsent(name, n -> new ArrayList<>()).add(curve);
        }
        return i;
    }

    public void writeObj(Writer writer) throws IOException {
        Obj merged = Objs.create();
        Set<String> mtls = new HashSet<>();
        for (var partEntry : modelParts.entrySet()) {
            MeshUtils.addAsGroup(partEntry.getValue(), partEntry.getKey(), merged);
            mtls.addAll(partEntry.getValue().getMtlFileNames());
        }
        merged.setMtlFileNames(mtls);

        ObjWriter.write(merged, writer);
    }

    public void readObj(Reader reader) throws IOException {
        Obj merged = ObjReader.read(reader);
        int numGroups = merged.getNumGroups();
        for (int i = 0; i < numGroups; i++) {
            ObjGroup group = merged.getGroup(i);
            Obj split = ObjUtils.groupToObj(merged, group, null);
            modelParts.put(group.getName(), split);
        }
    }
}
