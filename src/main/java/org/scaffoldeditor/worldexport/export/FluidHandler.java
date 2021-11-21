package org.scaffoldeditor.worldexport.export;

import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;

public final class FluidHandler {
    private FluidHandler() {}
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

        client.getBlockRenderManager().renderFluid(worldPos, world, consumer, fluidState);
        
        int index = context.fluidMeshes.size();
        context.fluidMeshes.add(mesh);
        return context.getFluidID(index);
    }
}
