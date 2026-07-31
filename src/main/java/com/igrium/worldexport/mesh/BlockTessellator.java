package com.igrium.worldexport.mesh;

import com.igrium.worldexport.mesh.VertexConsumers.ObjVertexConsumer;
import com.mojang.blaze3d.vertex.QuadInstance;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3f;

/**
 * Reimplementation of SectionCompiler to tessellate base world chunks
 */
public class BlockTessellator {

    public static final String WORLD = "world";
    public static final String WORLD_TRANS = "world_trans";
    public static final String GRASS_MAT = "grass_block";

    private final boolean ambientOcclusion;
    private final boolean cutoutLeaves;
    private final BlockStateModelSet blockModelSet;
    private final FluidStateModelSet fluidModelSet;
    private final BlockColors blockColors;

    public BlockTessellator(boolean ambientOcclusion, boolean cutoutLeaves, BlockStateModelSet blockModelSet,
                            FluidStateModelSet fluidModelSet, BlockColors blockColors) {
        this.ambientOcclusion = ambientOcclusion;
        this.cutoutLeaves = cutoutLeaves;
        this.blockModelSet = blockModelSet;
        this.fluidModelSet = fluidModelSet;
        this.blockColors = blockColors;
    }

    /**
     * Tessellate a world section into an obj
     *
     * @param sectionPos Section pos to tessellate
     * @param region     RenderSectionRegion to use
     * @return The tessellated obj, relative to the section root
     */
    public Obj compileSection(SectionPos sectionPos, BlockAndTintGetter region) {
        BlockPos minPos = sectionPos.origin();
        BlockPos maxPos = minPos.offset(15, 15, 15);
        VisGraph visGraph = new VisGraph();
        BlockModelLighter.enableCaching();

        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(ambientOcclusion, true, blockColors);
        FluidRenderer fluidRenderer = new FluidRenderer(fluidModelSet);

        Obj obj = Objs.create();

        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            obj.setActiveMaterialGroupName(WORLD_TRANS);
            addQuad(obj, quad, instance, true);
        };

        BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
            obj.setActiveMaterialGroupName(WORLD);
            addQuad(obj, quad, instance, false);
        };

        ObjVertexConsumer objConsumer = new ObjVertexConsumer(obj);

        FluidRenderer.Output fluidOutput = layer -> {
            obj.setActiveMaterialGroupName(WORLD_TRANS);
            return objConsumer;
        };

        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            BlockState blockState = region.getBlockState(pos);
            if (blockState.isAir()) continue;

            try {

                if (blockState.isSolidRender()) {
                    visGraph.setOpaque(pos);
                }

                if (blockState.hasBlockEntity()) {

                    // TODO: handle blockentity
                }

                FluidState fluidState = blockState.getFluidState();
                if (!fluidState.isEmpty()) {
                    fluidRenderer.tesselate(region, pos, fluidOutput, blockState, fluidState);
                }

                if (blockState.getRenderShape() == RenderShape.MODEL) {
                    blockRenderer.tesselateBlock(
                            ModelBlockRenderer.forceOpaque(this.cutoutLeaves, blockState) ? opaqueQuadOutput : quadOutput,
                            SectionPos.sectionRelative(pos.getX()),
                            SectionPos.sectionRelative(pos.getY()),
                            SectionPos.sectionRelative(pos.getZ()),
                            region, pos, blockState,
                            blockModelSet.get(blockState),
                            blockState.getSeed(pos)
                    );
                }

            } catch (Throwable t) {
                CrashReport report = CrashReport.forThrowable(t, "Tesselating block in replay export");
                CrashReportCategory category = report.addCategory("Block being tesselated");
                CrashReportCategory.populateBlockDetails(category, region, pos, blockState);
                throw new ReportedException(report);
            }
        }

        return obj;
    }


    private static void addQuad(Obj obj, BakedQuad quad, QuadInstance instance, boolean colored) {
        int n = obj.getNumVertices();
        for (int v = 0; v < 4; v++) {
            var pos = quad.position(v);
            if (colored) {
                obj.addVertex(new ColoredVertex(pos, unpackColor(instance.getColor(v), new Vector3f())));
            } else {
                obj.addVertex(pos.x(), pos.y(), pos.z());
            }

            long packedUv = quad.packedUV(v);
            obj.addTexCoord(UVPair.unpackU(packedUv), UVPair.unpackV(packedUv));
        }
        obj.addFace(n, n+1, n+2, n+3);
    }

    private static Vector3f unpackColor(int color, Vector3f dest) {
        dest.x = (color >> 16 & 0xFF) / 255f;
        dest.y = (color >> 8 & 0xFF) / 255f;
        dest.z = (color & 0xFF) / 255f;
        return dest;
    }

}
