package org.scaffoldeditor.worldexport.replaymod.camera_animations;

import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import net.minecraft.util.math.Vec3d;

public class CameraAnimationImpl extends AbstractCameraAnimation {

    protected final Vec3d[] positions;
    protected final Rotation[] rotations;
    protected final double[] fovs;

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
    public Rotation getRotation(int index) {
        return rotations[index];
    }

    @Override
    public double getFov(int index) {
        return fovs[index];
    }

    public CameraAnimationImpl(float fps, Vec3d[] positions, Rotation[] rotations, double[] fovs) throws IllegalArgumentException {
        if (positions.length != rotations.length || positions.length != fovs.length) {
            throw new IllegalArgumentException("All animation channels must be the same length!");
        }

        this.fps = fps;
        this.positions = positions;
        this.rotations = rotations;
        this.fovs = fovs;
    }
    
    public CameraAnimationImpl(float fps, List<Vec3d> positions, List<Rotation> rotations, List<Float> fovs) throws IllegalArgumentException {
        if (positions.size() != rotations.size() || positions.size() != fovs.size()) {
            throw new IllegalArgumentException("All animation channels must be the same length!");
        }

        this.fps = fps;
        this.positions = positions.toArray(new Vec3d[0]);
        this.rotations = rotations.toArray(new Rotation[0]);
        this.fovs = ArrayUtils.toPrimitive(fovs.toArray(new Double[0]));
    }
}
