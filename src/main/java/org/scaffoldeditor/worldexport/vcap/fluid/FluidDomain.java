package org.scaffoldeditor.worldexport.vcap.fluid;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.util.FloodFill;
import org.scaffoldeditor.worldexport.util.MeshComparator;
import org.scaffoldeditor.worldexport.vcap.BlockExporter;
import org.scaffoldeditor.worldexport.vcap.ExportContext;
import org.scaffoldeditor.worldexport.vcap.ObjVertexConsumer;
import org.scaffoldeditor.worldexport.vcap.model.ModelProvider.ModelInfo;
import org.scaffoldeditor.worldexport.vcap.model.VcapWorldMaterial;
import org.scaffoldeditor.worldexport.world_snapshot.ChunkView;

import com.google.common.collect.ImmutableMap;

import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;

/**
 * Represents a "domain" of fluid within a single frame.
 */
public class FluidDomain {
    private final Set<BlockPos> positions = new HashSet<>();
    private ModelInfo model;
    private final BlockPos rootPos;
    private final Fluid fluid;

    protected final ExportContext context;

    public FluidDomain(BlockPos rootPos, Fluid fluid, ExportContext context) {
        this.rootPos = rootPos;
        this.fluid = fluid;
        this.context = context;
    }

    /**
     * Get a all the block positions that this fluid occupies.
     * @return An unmodifiable set of all block positions.
     */
    public Set<BlockPos> getPositions() {
        return Collections.unmodifiableSet(positions);
    }

    public final ModelInfo getModel() {
        return model;
    }

    public final BlockPos getRootPos() {
        return rootPos;
    }

    public final Fluid getFluid() {
        return fluid;
    }

    /**
     * Check if this fluid domain collides with a given block.
     * @param position The block to check.
     * @return Whether it collides.
     */
    public boolean collidesWith(BlockPos position) {
        if (positions.contains(position)) return true;
        for (BlockPos pos : positions) {
            if (pos.getManhattanDistance(position) <= 1) return true;
        }
        return false;
    }

    /**
     * Check if this fluid domain "matches" another domain.
     * @param other The domain to check against.
     * @param comparator The mesh comparitor to use.
     * @return Whether it matches.
     */
    public boolean matches(FluidDomain other, MeshComparator comparator) {
        if (!positions.equals(other.positions)) return false;
        Vec3d offset = new Vec3d(
            other.rootPos.getX() - rootPos.getX(),
            other.rootPos.getY() - rootPos.getY(),
            other.rootPos.getZ() - rootPos.getZ());
        return comparator.meshEquals(model.mesh(), other.model.mesh(), offset, .01f, 0);
    }
    
    /**
     * Capture the fluid for this frame.
     * @param world The world to capture.
     */
    public void capture(ChunkView world) {
        Fluid rootFluid = world.getBlockState(rootPos).getFluidState().getFluid();
        if (rootFluid != fluid) {
            throw new IllegalStateException(
                    String.format("Fluid domain root position contains the wrong fluid! (%s != %s)",
                            getFluidName(rootFluid), getFluidName(fluid)));
        }

        // int fluidChunkSize = context.getSettings().getFluidChunkSize() / 2;
        FloodFill floodFill = context.getFloodFill().predicate(pos -> {
            if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()),
                    ChunkSectionPos.getSectionCoord(pos.getY())))
                return false;
            
            // if (Math.abs(pos.getX() - rootPos.getX()) > fluidChunkSize
            //         || Math.abs(pos.getY() - rootPos.getY()) > fluidChunkSize
            //         || Math.abs(pos.getZ() - rootPos.getZ()) > fluidChunkSize) {
            //     return false;
            // }

            return (world.getBlockState(pos).getFluidState().isOf(fluid));
        }).function(pos -> {
            if (context.getSettings().isInExport(pos)) {
                positions.add(pos);
            }
        }).maxDepth(5000).build();
        floodFill.execute(rootPos);

        this.model = captureMesh(world);
    }

    protected ModelInfo captureMesh(BlockRenderView world) {
        MinecraftClient client = MinecraftClient.getInstance();

        BlockState rootState = world.getBlockState(rootPos);

        Obj mesh = Objs.create();
        VcapWorldMaterial material = new VcapWorldMaterial(true, true,
                rootState.getLuminance() >= BlockExporter.EMISSIVE_THRESHOLD);
        mesh.setActiveMaterialGroupName(material.getName());

        ObjVertexConsumer consumer = new ObjVertexConsumer(mesh);

        for (BlockPos pos : positions) {
            Vector3d offset = new Vector3d(-(pos.getX() & 15), -(pos.getY() & 15), -(pos.getZ() & 15));
            BlockPos blockOffset = pos.subtract(rootPos);
            offset.add(blockOffset.getX(), blockOffset.getY(), blockOffset.getZ());

            consumer.setTransform(offset);

            BlockState state = world.getBlockState(pos);
            client.getBlockRenderManager().renderFluid(pos, world, consumer, state, state.getFluidState());
        }

        return new ModelInfo(mesh, 1, ImmutableMap.of(material.getName(), material));
    }

    private String getFluidName(Fluid fluid) {
        return Registries.FLUID.getId(fluid).toString();
    }
}
