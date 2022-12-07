package org.scaffoldeditor.worldexport.replaymod.animation_serialization;

import java.util.HashSet;
import java.util.Set;

import org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannel.RotationProvidingChannel;
import org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannel.ScalarProvidingChannel;
import org.scaffoldeditor.worldexport.replaymod.animation_serialization.AnimationChannel.VectorProvidingChannel;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class AnimationChannels {
    public static final BiMap<String, AnimationChannel<?>> channelTypeRegistry = HashBiMap.create();

    public static final AnimationChannel.VectorChannel LOCATION = new AnimationChannel.VectorChannel();
    public static final AnimationChannel.EulerChannel ROTATION_EULER = new AnimationChannel.EulerChannel();
    public static final AnimationChannel.QuaternionChannel ROTATION_QUAT = new AnimationChannel.QuaternionChannel();
    public static final AnimationChannel.DoubleChannel FOV = new AnimationChannel.DoubleChannel();

    public static final Set<VectorProvidingChannel<?>> positionChannels = new HashSet<>();
    public static final Set<RotationProvidingChannel<?>> rotationChannels = new HashSet<>();
    public static final Set<ScalarProvidingChannel<?>> fovChannels = new HashSet<>();

    static {
        channelTypeRegistry.put("location", LOCATION);
        channelTypeRegistry.put("rotation_euler", ROTATION_EULER);
        channelTypeRegistry.put("rotation_quat", ROTATION_QUAT);
        channelTypeRegistry.put("fov", FOV);

        positionChannels.add(LOCATION);
        rotationChannels.add(ROTATION_EULER);
        rotationChannels.add(ROTATION_QUAT);
        fovChannels.add(FOV);
    }

    public static AnimationChannel<?> getChannel(String id) {
        return channelTypeRegistry.get(id);
    }

    public static String getId(AnimationChannel<?> channel) {
        return channelTypeRegistry.inverse().get(channel);
    }
    
}
