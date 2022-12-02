package org.scaffoldeditor.worldexport.replaymod.camera_paths;

import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import net.minecraft.util.math.Vec3d;

public class CameraAnimation extends AbstractCameraAnimation {

    protected final Vec3d[] positions;
    protected final Vec3d[] rotations;
    protected final float[] fovs;

    protected float fps = 30;

    @Override
    public double getFps() {
        return fps;
    }

    @Override
    public int size() {
        return positions.length;
    }

    @Override
    public Vec3d getPosition(int index) {
        return positions[index];
    }

    @Override
    public Vec3d getRotation(int index) {
        return rotations[index];
    }

    @Override
    public float getFov(int index) {
        return fovs[index];
    }

    public CameraAnimation(float fps, Vec3d[] positions, Vec3d[] rotations, float[] fovs) throws IllegalArgumentException {
        if (positions.length != rotations.length || positions.length != fovs.length) {
            throw new IllegalArgumentException("All animation channels must be the same length!");
        }

        this.fps = fps;
        this.positions = positions;
        this.rotations = rotations;
        this.fovs = fovs;
    }
    
    public CameraAnimation(float fps, List<Vec3d> positions, List<Vec3d> rotations, List<Float> fovs) throws IllegalArgumentException {
        if (positions.size() != rotations.size() || positions.size() != fovs.size()) {
            throw new IllegalArgumentException("All animation channels must be the same length!");
        }

        this.fps = fps;
        this.positions = positions.toArray(new Vec3d[0]);
        this.rotations = rotations.toArray(new Vec3d[0]);
        this.fovs = ArrayUtils.toPrimitive(fovs.toArray(new Float[0]));
    }
}
