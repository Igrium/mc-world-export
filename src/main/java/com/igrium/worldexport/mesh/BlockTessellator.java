package com.igrium.worldexport.mesh;

import com.igrium.worldexport.mesh.VertexConsumers.ObjVertexConsumer;
import com.mojang.blaze3d.vertex.QuadInstance;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import lombok.Builder;
import lombok.NonNull;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3f;

import java.util.Collections;

/**
 * Reimplementation of SectionCompiler to tessellate base world chunks
 */
@Builder
public class BlockTessellator {

    @Builder.Default
    private final boolean ambientOcclusion = true;

    private final @NonNull BlockStateModelSet blockModelSet;
    private final @NonNull FluidStateModelSet fluidModelSet;
    private final @NonNull BlockColors blockColors;

    private final @NonNull BlockMaterialFactory blockMatFactory;
    private final @NonNull FluidMaterialFactory fluidMatFactory;

    @Builder.Default
    private final boolean splitBlocks = true;

    @Builder.Default
    private final boolean mergeDoubles = true;

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

        ObjVertexConsumer objConsumer = new ObjVertexConsumer(obj);

        BlockQuadOutput quadOutput = (x, y, z, quad, instance) ->
                addQuad(obj, x, y, z, quad, instance, true);

        FluidRenderer.Output fluidOutput = _ -> objConsumer;


        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            BlockState blockState = region.getBlockState(pos);
            if (blockState.isAir()) continue;

            try {

                if (blockState.isSolidRender()) {
                    visGraph.setOpaque(pos);
                }

                if (blockState.hasBlockEntity()) {
                    // TODO: do we need to handle block entities here?
                }

                FluidState fluidState = blockState.getFluidState();
                if (!fluidState.isEmpty()) {
                    obj.setActiveMaterialGroupName(fluidMatFactory.getMaterial(fluidState));
                    if (splitBlocks) {
                        Identifier id = BuiltInRegistries.FLUID.getKey(fluidState.getType());
                        obj.setActiveGroupNames(Collections.singletonList("fluid." + id));
                    }
                    fluidRenderer.tesselate(region, pos, fluidOutput, blockState, fluidState);
                }

                if (blockState.getRenderShape() == RenderShape.MODEL) {
                    obj.setActiveMaterialGroupName(blockMatFactory.getMaterial(blockState));
                    if (splitBlocks) {
                        Identifier id = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
                        obj.setActiveGroupNames(Collections.singletonList(id.toString()));
                    }
                    blockRenderer.tesselateBlock(
                            quadOutput,
                            SectionPos.sectionRelative(pos.getX()),
                            SectionPos.sectionRelative(pos.getY()),
                            SectionPos.sectionRelative(pos.getZ()),
                            region, pos, blockState,
                            blockModelSet.get(blockState),
                            blockState.getSeed(pos)
                    );
                }

            } catch (Throwable t) {
                CrashReport report = CrashReport.forThrowable(t, "Tessellating block in replay export");
                CrashReportCategory category = report.addCategory("Block being tesselated");
                CrashReportCategory.populateBlockDetails(category, region, pos, blockState);
                throw new ReportedException(report);
            }
        }

        return obj;
    }


    private static void addQuad(Obj obj, float x, float y, float z, BakedQuad quad, QuadInstance instance, boolean colored) {
        int n = obj.getNumVertices();
        for (int v = 0; v < 4; v++) {
            // Quad positions are model-local
            var pos = quad.position(v).add(x, y, z, new Vector3f());
            if (colored) {
                obj.addVertex(new ColoredVertex(pos, unpackColor(instance.getColor(v), new Vector3f())));
            } else {
                obj.addVertex(pos.x(), pos.y(), pos.z());
            }

            long packedUv = quad.packedUV(v);
            obj.addTexCoord(UVPair.unpackU(packedUv), 1 - UVPair.unpackV(packedUv));
        }
        obj.addFaceWithTexCoords(n, n + 1, n + 2, n + 3);
    }

    private static Vector3f unpackColor(int color, Vector3f dest) {
        dest.x = (color >> 16 & 0xFF) / 255f;
        dest.y = (color >> 8 & 0xFF) / 255f;
        dest.z = (color & 0xFF) / 255f;
        return dest;
    }

}
