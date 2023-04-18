package org.scaffoldeditor.worldexport.vcap;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;
import org.scaffoldeditor.worldexport.vcap.fluid.FluidBlockEntry;
import org.scaffoldeditor.worldexport.vcap.model.MaterialProvider;
import org.scaffoldeditor.worldexport.vcap.model.SpriteMaterialProvider;
import org.scaffoldeditor.worldexport.vcap.model.VcapWorldMaterial;
import org.scaffoldeditor.worldexport.vcap.model.ModelProvider.ModelInfo;

import com.google.common.collect.ImmutableMap;

import de.javagl.obj.FloatTuple;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjFace;
import de.javagl.obj.Objs;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

public final class MeshWriter {
    private MeshWriter() {}

    public static final String EMPTY_MESH = "empty";

    public static ModelInfo writeBlockMesh(BlockModelEntry entry, Random random) {
        Obj obj = Objs.create();
        BakedModel model = entry.model();
        BlockState blockState = entry.blockState();
        boolean transparent = entry.transparent();
        boolean emissive = entry.emissive();

        List<Set<float[]>> fLayers = new ArrayList<>();
        Map<String, MaterialProvider> materials = new HashMap<>();

        for (Direction direction : Direction.values()) {
            if (!entry.isFaceVisible(direction)) continue;
            List<BakedQuad> quads = model.getQuads(blockState, direction, random);
            for (BakedQuad quad : quads) {
                addFace(quad, obj, transparent, emissive, fLayers, materials::put);
            }
        }
        {
            // Quads that aren't assigned to a direction.
            List<BakedQuad> quads = model.getQuads(blockState, null, random);
            for (BakedQuad quad : quads) {
                addFace(quad, obj, transparent, emissive, fLayers, materials::put);
            }
        }
        
        return new ModelInfo(obj, fLayers.size(), materials);
    }

    /**
     * Add a baked quad to a 3d mesh.
     * 
     * @param quad             Quad to add.
     * @param obj              Mesh to add to.
     * @param transparent      Assign transparent material.
     * @param fLayers          A list of sets of 12-float arrays indicating what
     *                         quads already exist. Used for material stacking.
     * @param materialConsumer For all the generated vcap world materials.
     * @return The face layer index this face was added to.
     */
    private static int addFace(BakedQuad quad, Obj obj, boolean transparent, boolean emissive,
            @Nullable List<Set<float[]>> fLayers, BiConsumer<String, MaterialProvider> materialConsumer) {

        Sprite sprite = quad.getSprite();

        boolean useAnimation = sprite.createAnimation() != null;
        MaterialProvider material;
        String matName;

        if (useAnimation) {
            SpriteMaterialProvider mat = new SpriteMaterialProvider(sprite, transparent, quad.hasColor(), emissive);
            matName = mat.getName();
            material = mat;
        } else {
            VcapWorldMaterial mat = new VcapWorldMaterial(transparent, quad.hasColor(), emissive);
            matName = mat.getName();
            material = mat;
        }

        materialConsumer.accept(matName, material);
        obj.setActiveMaterialGroupName(matName);

        int[] vertData = quad.getVertexData();

        int len = vertData.length / 8;
        ByteBuffer buffer = ByteBuffer.allocate(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.getVertexSizeByte());
        IntBuffer intBuffer = buffer.asIntBuffer();

        int[] indices = new int[len];
        float[] vertices = new float[len * 4];

        for (int i = 0; i < len; i++) {
            indices[i] = obj.getNumVertices();

            intBuffer.clear();
            intBuffer.put(vertData, i * 8, 8);

            float x = buffer.getFloat(0);
            float y = buffer.getFloat(4);
            float z = buffer.getFloat(8);

            float u = buffer.getFloat(16);
            float v = buffer.getFloat(20);

            // Convert to sprite coordinates
            if (useAnimation) {
                u = makeLocal(sprite.getMinU(), sprite.getMaxU(), u);
                v = makeLocal(sprite.getMinV(), sprite.getMaxV(), v);
            }

            obj.addTexCoord(u, 1 - v);
            obj.addVertex(x, y, z);

            vertices[i * 3] = x;
            vertices[i * 3 + 1] = y;
            vertices[i * 3 + 1] = z;
        }

        // Identify the first layer without this face.
        int layerIndex = 0;

        if (fLayers != null) {
            if (fLayers.isEmpty()) {
                fLayers.add(new HashSet<>());
            }

            int i = 0;
            Set<float[]> layer = fLayers.get(i);
            while (contains(layer, vertices)) {
                i++;
                if (fLayers.size() >= i) {
                    fLayers.add(new HashSet<>());
                }
                layer = fLayers.get(i);
            }
            layerIndex = i;
            layer.add(vertices);
        }

        obj.setActiveGroupNames(Arrays.asList(genGroupName(layerIndex)));
        obj.addFace(indices, indices, null);

        return layerIndex;
    }

    public static FluidBlockEntry writeFluidMesh(BlockPos pos, BlockRenderView world, BlockState state) {
        FluidState fluidState = state.getFluidState();
        if (fluidState == null || fluidState.isEmpty()) {
            throw new IllegalArgumentException("Supplied blockstate must be a fluid.");
        }

        Obj mesh = Objs.create();
        VcapWorldMaterial material = new VcapWorldMaterial(true, true,
                state.getLuminance() > BlockExporter.EMISSIVE_THRESHOLD);
        mesh.setActiveMaterialGroupName(material.getName());

        Vec3d offset = new Vec3d(-(pos.getX() & 15), -(pos.getY() & 15), -(pos.getZ() & 15));
        ObjVertexConsumer consumer = new ObjVertexConsumer(mesh, offset);

        MinecraftClient.getInstance().getBlockRenderManager().renderFluid(pos, world, consumer, state, state.getFluidState());

        ModelInfo modelInfo = new ModelInfo(mesh, 1, ImmutableMap.of(material.getName(), material));
        return new FluidBlockEntry(fluidState.getFluid(), modelInfo);
    }

    private static float makeLocal(float globalMin, float globalMax, float globalVal) {
        return (globalVal - globalMin) / (globalMax - globalMin);
    }

    private static boolean contains(Collection<float[]> collection, float[] array) {
        for (float[] val : collection) {
            if (Arrays.equals(val, array)) return true;
        }
        return false;
    }
    
    public static String genGroupName(int index) {
        return "fLayer"+index;
    }

    /**
     * Determine whether two meshes are equivilent.
     * 
     * Note: Only checks if verts are the same for now.
     * @param first The first mesh.
     * @param second The second mesh.
     * @return Whether they are equivilent.
     */
    @Deprecated
    public static boolean objEquals(Obj first, Obj second) {
        if (first.getNumVertices() != second.getNumVertices()) return false;
        if (first.getNumFaces() != second.getNumFaces()) return false;
        if (first.getNumNormals() != second.getNumNormals()) return false;

        Set<ObjFace> used = new HashSet<>();

        for (int i = 0; i < first.getNumFaces(); i++) {
            ObjFace secondFace = findFace(first, second, i, used);
            if (secondFace == null) return false;
            used.add(secondFace);
        }

        return true;
    }

    private static ObjFace findFace(Obj first, Obj second, int index, Collection<ObjFace> exclude) {
        ObjFace firstFace = first.getFace(index);
        for (int i = 0; i < second.getNumFaces(); i++) {
            ObjFace secondFace = second.getFace(i);
            if (exclude.contains(secondFace)) continue;
            if (faceEquals(firstFace, secondFace, first, second)) return secondFace;
        }
        return null;
    }

    private static boolean faceEquals(ObjFace first, ObjFace second,
        Obj firstObj, Obj secondObj) {
        if (first.getNumVertices() != second.getNumVertices()) return false;
        for (int i = 0; i < first.getNumVertices(); i++) {
            if (!tupleEquals(firstObj.getVertex(first.getVertexIndex(i)),
                secondObj.getVertex(second.getVertexIndex(i)))) {
                return false;
            }
            if (!tupleEquals(firstObj.getNormal(first.getNormalIndex(i)),
                secondObj.getNormal(second.getNormalIndex(i)))) {
                return false;
            }
            if (!tupleEquals(firstObj.getTexCoord(first.getTexCoordIndex(i)),
                secondObj.getTexCoord(second.getTexCoordIndex(i)))) {
                return false;
            }
        }

        return true;
    }
 
    private static boolean tupleEquals(FloatTuple first, FloatTuple second) {
        try {
            return (first.getDimensions() == second.getDimensions())
            && (first.getW() == second.getW())
            && (first.getX() == second.getX())
            && (first.getY() == second.getY())
            && (first.getZ() == second.getZ());
        } catch (ArrayIndexOutOfBoundsException e) {
            return true; // If we hit the end of the tuple, all prior checks worked.
        }
    }
}
