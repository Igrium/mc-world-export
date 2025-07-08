package com.igrium.worldexport.replay;

import com.igrium.worldexport.world.WorldMesh;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class CapturedReplay {

    @Getter
    private final List<WorldMesh> worldMeshes = new ArrayList<>();

}
