package com.igrium.worldexport.world.mesh;

import de.javagl.obj.Obj;
import lombok.Getter;
import lombok.Setter;
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
    }

    public WorldMesh(Obj obj) {
        this(obj, new Meta());
    }

    public WorldMesh(Obj obj, @Nullable Integer startTick, @Nullable Integer endTick) {
        this(obj);
        meta.startTick = startTick;
        meta.endTick = endTick;
    }

}
