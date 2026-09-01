package com.igrium.worldexport.blockentity;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.ModelParts;
import com.igrium.worldexport.replay.MaterialHolder;
import com.igrium.worldexport.tex.MaterialGen;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.TextureExtractor;
import de.javagl.obj.Mtls;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Collections;

public abstract class ModelBlockModelAdapter<T extends BlockEntity, S extends BlockEntityRenderState, M extends Model<?>> extends BlockModelAdapter<T, S> {

    public ModelBlockModelAdapter(BlockEntityRenderer<? super T, S> renderer) {
        super(renderer);
    }

    @Override
    public void capture(T blockEntity, S state, CapturedEntity capture, MaterialHolder materials, Vec3 offset, int tick) {
        setupTransforms(blockEntity, state, capture, offset, tick);

        M model = getModel(state);
        setupAnim(model, state);

        ModelParts.captureModelPose(model.root(), "root", AnimationCurve.CurveFormat.POS_ROT, capture, tick, true);

        captureBaseModel(state, capture, materials);
    }

    protected void setupTransforms(T blockEntity, S state, CapturedEntity capture, Vec3 offset, int tick) {
        Vec3 pos = Vec3.atLowerCornerOf(blockEntity.getBlockPos()).add(offset);
        capture.addFrame(CapturedEntity.ROOT_NAME, tick, AnimationCurve.CurveFormat.POS,
                pos.toVector3f(), null, null);
    }

    protected void captureBaseModel(S state, CapturedEntity capture, MaterialHolder materials) {
        captureBaseModel("root", state, capture, materials);
    }

    protected void captureBaseModel(String rootName, S state, CapturedEntity capture, MaterialHolder materials) {
        M model = getModel(state);
        if (model == null) return;

        // Extract texture
        Identifier texId = getTexture(state);
        String texName = MaterialGen.getTexturePath(texId);

        materials.getTextures().computeIfAbsent(texName, tex -> TextureExtractor.pullTextureAsync(texId));

        ReplayMtl mat = materials.getOrCreateMtl("entities.mtl", texName, n -> {
            ReplayMtl mtl = new ReplayMtl(Mtls.create(n));
            mtl.mtl().setMapKd(texName);
            mtl.mtl().setMapD(texName);
            return mtl;
        });

        ModelParts.buildParentHierarchy(model.root(), rootName, capture.getParents()::put);

        // Add part meshes if needed
        ModelParts.forEachPart(model.root(), rootName, (path, part) -> {
            capture.getModelParts().computeIfAbsent(path, _ -> {
                Obj obj = Objs.create();
                obj.setMtlFileNames(Collections.singleton("entities.mtl"));
                obj.setActiveMaterialGroupName(mat.getName());
                return ModelParts.modelPartToMesh(part, obj);
            });
        });
    }

    protected abstract M getModel(S state);

    protected abstract void setupAnim(M model, S state);

    protected abstract Identifier getTexture(S state);
}
