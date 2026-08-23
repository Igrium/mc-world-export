package com.igrium.worldexport.mesh.tessellate;

import com.igrium.worldexport.mesh.vertex.ColoredVertex;
import com.igrium.worldexport.mesh.vertex.ObjVertexConsumer;
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
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.function.Function;

@Builder
public class BlockTessellator {
    public interface QuadConsumerFactory {
        /**
         * Called once per block to do per-block operations (override checking, etc.)
         * @param state Block being tessellated
         * @return Quad consumer for that block.
         */
        QuadConsumer forState(Obj obj, BlockState state);
    }

    public interface QuadConsumer {
        void accept(Obj obj, float x, float y, float z, BakedQuad quad, QuadInstance instance);
    }

    /**
     * Write a baked quad into an obj, applying the position offset and vertex colors.
     *
     * @param obj      Obj to write into.
     * @param x        Block X, relative to the obj origin.
     * @param y        Block Y, relative to the obj origin.
     * @param z        Block Z, relative to the obj origin.
     * @param quad     Quad to write.
     * @param instance Quad instance holding the baked vertex colors.
     */
    public static void addQuad(Obj obj, float x, float y, float z, BakedQuad quad, QuadInstance instance) {
        int n = obj.getNumVertices();
        for (int idx = 0; idx < 4; idx++) {
            // Quad positions are model-local
            var pos = quad.position(idx).add(x, y, z, new Vector3f());
            obj.addVertex(new ColoredVertex(pos, unpackColor(instance.getColor(idx), new Vector3f())));

            long packedUv = quad.packedUV(idx);
            obj.addTexCoord(UVPair.unpackU(packedUv), 1 - UVPair.unpackV(packedUv));
        }
        obj.addFaceWithTexCoords(n, n + 1, n + 2, n + 3);
    }

    // So much variable bloat because this is called for every quad and Java doesn't have structs lol
    public static void addQuad(Obj obj, float x, float y, float z, BakedQuad quad, QuadInstance instance,
                               float u0, float v0,
                               float u1, float v1,
                               float u2, float v2,
                               float u3, float v3) {
        int n = obj.getNumVertices();
        for (int idx = 0; idx < 4; idx++) {
            // Quad positions are model-local
            var pos = quad.position(idx).add(x, y, z, new Vector3f());
            obj.addVertex(new ColoredVertex(pos, unpackColor(instance.getColor(idx), new Vector3f())));

            float u = switch(idx) {
                case 0 -> u0;
                case 1 -> u1;
                case 2 -> u2;
                case 3 -> u3;
                default -> throw new IllegalStateException("Unexpected value: " + idx);
            };

            float v = switch(idx) {
                case 0 -> v0;
                case 1 -> v1;
                case 2 -> v2;
                case 3 -> v3;
                default -> throw new IllegalStateException("Unexpected value: " + idx);
            };
            obj.addTexCoord(u, 1 - v);
        }
        obj.addFaceWithTexCoords(n, n + 1, n + 2, n + 3);
    }

    private static Vector3f unpackColor(int color, Vector3f dest) {
        dest.x = (color >> 16 & 0xFF) / 255f;
        dest.y = (color >> 8 & 0xFF) / 255f;
        dest.z = (color & 0xFF) / 255f;
        return dest;
    }

    private final @NonNull BlockStateModelSupplier blockModelSet;
    private final @NonNull FluidStateModelSet fluidModelSet;
    private final @NonNull BlockColors blockColors;

    private final @NonNull Function<FluidState, String> fluidMatFactory;
    private final @NonNull QuadConsumerFactory quadConsumerFactory;

    @Builder.Default
    private final boolean splitBlocks = true;

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

        return compileBlocks(BlockPos.betweenClosed(minPos, maxPos), region, minPos);
    }

    /**
     * Tessellate an arbitrary collection of blocks into an obj.
     *
     * @param blocks Blocks to tessellate. Does not affect culling.
     * @param region RenderSectionRegion to use
     * @param origin Position the resulting geometry is relative to
     * @return The tessellated obj, relative to <code>origin</code>
     * @implNote {@link FluidRenderer} computes its vertex positions section-locally, so every block passed in
     * must belong to the section starting at <code>origin</code>. Blocks outside it will have their fluid
     * geometry placed incorrectly.
     */
    public Obj compileBlocks(Iterable<BlockPos> blocks, BlockAndTintGetter region, BlockPos origin) {
        VisGraph visGraph = new VisGraph();
        BlockModelLighter.enableCaching();

        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(false, true, blockColors);
        FluidRenderer fluidRenderer = new FluidRenderer(fluidModelSet);

        Obj obj = Objs.create();
        ObjVertexConsumer objConsumer = new ObjVertexConsumer(obj);

        final Mutable<QuadConsumer> curQuadConsumer = new MutableObject<>();

        BlockQuadOutput perFaceQuadOutput = (x, y, z, quad, instance) -> {
            curQuadConsumer.get().accept(obj, x, y, z, quad, instance);
        };

        // TODO: update based on layer
        FluidRenderer.Output fluidOutput = _ -> objConsumer;

        for (BlockPos pos : blocks) {
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
                    String fluidMat = fluidMatFactory.apply(fluidState);
                    obj.setActiveMaterialGroupName(fluidMat);
                    if (splitBlocks) {
                        Identifier id = BuiltInRegistries.FLUID.getKey(fluidState.getType());
                        obj.setActiveGroupNames(Collections.singletonList("fluid." + id));
                    }
                    fluidRenderer.tesselate(region, pos, fluidOutput, blockState, fluidState);
                }

                if (blockState.getRenderShape() == RenderShape.MODEL) {
                    if (splitBlocks) {
                        Identifier id = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
                        obj.setActiveGroupNames(Collections.singletonList(id.toString()));
                    }
                    curQuadConsumer.setValue(quadConsumerFactory.forState(obj, blockState));
                    blockRenderer.tesselateBlock(
                            perFaceQuadOutput,
                            pos.getX() - origin.getX(),
                            pos.getY() - origin.getY(),
                            pos.getZ() - origin.getZ(),
                            region, pos, blockState,
                            blockModelSet.get(blockState),
                            blockState.getSeed(pos)
                    );
                }

            } catch (Throwable t) {
                CrashReport report = CrashReport.forThrowable(t, "Tessellating block in replay export");
                CrashReportCategory category = report.addCategory("Block being tessellated");
                CrashReportCategory.populateBlockDetails(category, region, pos, blockState);
                throw new ReportedException(report);
            }
        }
        return obj;
    }


}
