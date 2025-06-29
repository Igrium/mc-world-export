package com.igrium.worldexport.world.mesh;

import de.javagl.obj.Obj;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

/**
 * A mesh that can optionally have an in point and out point.
 */
@Setter @Getter
public class WorldMesh {

    private final Obj mesh;

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

    public WorldMesh(Obj mesh) {
        this.mesh = mesh;
    }


}
