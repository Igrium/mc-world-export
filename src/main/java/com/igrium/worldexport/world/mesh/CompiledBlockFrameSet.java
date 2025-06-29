package com.igrium.worldexport.world.mesh;

import com.igrium.worldexport.world.WorldCapture;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.*;

/**
 * Keeps track of block update frames in a format ideal for tessellation.
 * @apiNote In this implementation, blocks are given a keyframe if any adjoining blocks are updated.
 */
public class CompiledBlockFrameSet {
    private final Int2ObjectSortedMap<List<BlockPos>> blockKeyframeReferences = new Int2ObjectAVLTreeMap<>();
    private final Int2ObjectSortedMap<List<ChunkSectionPos>> sectionKeyframeReferences = new Int2ObjectAVLTreeMap<>();


    private void generate(WorldCapture capture) {

    }
}
