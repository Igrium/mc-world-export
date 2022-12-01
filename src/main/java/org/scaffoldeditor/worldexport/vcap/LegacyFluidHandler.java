package org.scaffoldeditor.worldexport.vcap;

import java.util.Map;

import javax.annotation.Nullable;

import org.scaffoldeditor.worldexport.util.MeshComparator;

import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;

@Deprecated
public final class LegacyFluidHandler {
    private LegacyFluidHandler() {}
    static final MinecraftClient client = MinecraftClient.getInstance();

    /**
     * Generate a fluid mesh.
     * @param world World to get fluid from.
     * @param worldPos The position of the block.
     * @param context The current export context.
     * @param fluidState Fluid state to render.
     * @return ID of the fluid mesh.
     */
    public static String writeFluidMesh(BlockRenderView world, BlockPos worldPos, ExportContext context, FluidState fluidState) {
        if (fluidState.isEmpty()) {
            throw new IllegalArgumentException("Empty fluid state.");
        }

        Obj mesh = Objs.create();
        mesh.setActiveMaterialGroupName(MeshWriter.TRANSPARENT_TINTED_MAT);
        Vec3d offset = new Vec3d(-(worldPos.getX() & 15), -(worldPos.getY() & 15), -(worldPos.getZ() & 15));
        ObjVertexConsumer consumer = new ObjVertexConsumer(mesh, offset);

        client.getBlockRenderManager().renderFluid(worldPos, world, consumer, world.getBlockState(worldPos), fluidState);
        
        return addFluidMeshToContext(context, mesh);
    }

    @Nullable
    private static String findMeshInContext(Obj mesh, ExportContext context) {
        for (Map.Entry<String, Obj> entry : context.extraModels.entrySet()) {
            if (context.getMeshComparator().meshEquals(mesh, entry.getValue(), .05f,
                    MeshComparator.LENIENT_FACE_MATCHING | MeshComparator.NO_SORT)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static String addFluidMeshToContext(ExportContext context, Obj mesh) {
        if (mesh.getNumVertices() == 0) {
            return MeshWriter.EMPTY_MESH;
        }
        Map<String, Obj> fluidMeshes = context.extraModels;

        String existing = findMeshInContext(mesh, context);
        if (existing != null) return existing;

        String name = genFluidMeshName(context);
        fluidMeshes.put(name, mesh);
        return name;
    }

    private static String genFluidMeshName(ExportContext context) {
        int index = 0;
        while (context.extraModels.keySet().contains("fluid."+index)) {
            index++;
        }
        return "fluid."+index;
    }
}
