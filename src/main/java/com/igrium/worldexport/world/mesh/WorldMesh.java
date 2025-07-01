package com.igrium.worldexport.world.mesh;

import com.google.gson.annotations.JsonAdapter;
import com.igrium.worldexport.util.JsonAdapters;
import de.javagl.obj.Obj;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * A mesh that can optionally have an in point and out point.
 */
public record WorldMesh(Obj obj, Meta meta) {

    @Getter @Setter
    public static class Meta {
        /**
         * The first tick where the mesh will be visible.
         */
        @Nullable
        private Integer startTick;

        /**
         * The last tick where the mesh will be visible.
         */
        @Nullable
        private Integer endTick;

        @NonNull
        @JsonAdapter(JsonAdapters.Vec3dAdapter.class)
        private Vec3d offset = Vec3d.ZERO;
    }

    public WorldMesh(Obj obj) {
        this(obj, new Meta());
    }

    public WorldMesh(Obj obj, @Nullable Integer startTick, @Nullable Integer endTick) {
        this(obj);
        meta.startTick = startTick;
        meta.endTick = endTick;
    }

    public WorldMesh(Obj obj, Vec3d offset) {
        this(obj);
        meta.offset = offset;
    }

    public WorldMesh(Obj obj, Vec3d offset, @Nullable Integer startTick, @Nullable Integer endTick) {
        this(obj, offset);
        meta.startTick = startTick;
        meta.endTick = endTick;
    }

}