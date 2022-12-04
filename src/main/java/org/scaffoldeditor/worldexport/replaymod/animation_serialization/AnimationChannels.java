package org.scaffoldeditor.worldexport.replaymod.animation_serialization;

import java.util.HashSet;
import java.util.Set;

import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation.Euler;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation.QuaternionRot;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.Rotation.RotationChannel;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import net.minecraft.util.math.Vec3d;

public class AnimationChannels {
    public static final BiMap<String, AnimationChannel<?>> channelTypeRegistry = HashBiMap.create();

    public static final AnimationChannel<Vec3d> LOCATION = new AnimationChannel.VectorChannel();
    public static final RotationChannel<Euler> ROTATION_EULER = new Rotation.EulerChannel();
    public static final RotationChannel<QuaternionRot> ROTATION_QUAT = new Rotation.QuaternionChannel();
    public static final AnimationChannel<Float> FOV = new AnimationChannel.FloatChannel();

    public static final Set<String> positionChannels = new HashSet<>();
    public static final Set<String> rotationChannels = new HashSet<>();
    public static final Set<String> fovChannels = new HashSet<>();

    static {
        channelTypeRegistry.put("location", LOCATION);
        channelTypeRegistry.put("rotation_euler", ROTATION_EULER);
        channelTypeRegistry.put("rotation_quat", ROTATION_QUAT);
        channelTypeRegistry.put("fov", FOV);

        positionChannels.add("location");
        rotationChannels.add("rotation_euler");
        fovChannels.add("fov");
    }
}
