package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.model_adapters.ItemModelAdapter;
import com.igrium.worldexport.mesh.postprocess.MeshUtils;
import com.igrium.worldexport.mesh.vertex.ObjVertexConsumer;
import com.igrium.worldexport.replay.MaterialHolder;
import com.mojang.blaze3d.vertex.QuadInstance;
import de.javagl.obj.Objs;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import java.util.*;

/** Capture resolved item quads as variants, retaining submission transforms as curves. */
public final class ItemMeshCapture {
    private record Key(List<BakedQuad> quads, ItemStackRenderState.FoilType foil, List<Integer> tints) {}
    private final Map<Key,String> names=new HashMap<>();

    public List<String> capture(CapturedEntity capture, MaterialHolder materials, ItemQuadCollector collector,
                        String prefix, String parent, Matrix4fc relativeToParent, int tick) {
        if (!collector.getIgnoredSubmissions().isEmpty())
            for (String ignored : collector.getIgnoredSubmissions()) capture.getCaptureWarnings().add("item_submission_not_captured:"+ignored);
        List<String> activeParts=new ArrayList<>();
        for (var submission : collector.getSubmissions()) {
            if (submission.quads().isEmpty()) continue;
            List<Integer> tints=Arrays.stream(submission.tintLayers()).boxed().toList();
            var key=new Key(List.copyOf(submission.quads()),submission.foilType(),tints);
            String name=prefix+names.computeIfAbsent(key,k -> Integer.toString(names.size()));
            activeParts.add(name);
            var parts=capture.getAppearance().has("itemParts") ? capture.getAppearance().getAsJsonObject("itemParts") : new com.google.gson.JsonObject();
            if (!parts.has(name)) {
                var info=new com.google.gson.JsonObject();info.addProperty("parent",parent);
                var textures=new com.google.gson.JsonArray();
                submission.quads().stream().map(q -> q.materialInfo().sprite().contents().name()).distinct().forEach(id ->
                    textures.add(id.getNamespace()+"/textures/"+id.getPath()+".png"));
                info.add("textures",textures);parts.add(name,info);
                capture.getAppearance().add("itemParts",parts);
            }
            capture.getParents().put(name,parent);
            var local=new Matrix4f(relativeToParent).mul(submission.pose().pose());
            var pose=com.igrium.worldexport.entity.CaptureTransforms.decompose(local);
            if (pose.sheared()) capture.getCaptureWarnings().add("item_transform_contains_shear");
            capture.addFrame(name,tick,AnimationCurve.CurveFormat.POS_ROT_SCALE,pose.position(),pose.rotation(),pose.scale());
            capture.getModelParts().computeIfAbsent(name,n -> {
                var obj=Objs.create();obj.setMtlFileNames(Set.of("entities.mtl"));
                var consumer=new ObjVertexConsumer(obj);
                for (var quad : submission.quads()) {
                    var material=ItemModelAdapter.getOrCreateItemMaterial(materials,
                            submission.foilType()!=ItemStackRenderState.FoilType.NONE,quad.materialInfo().sprite().atlasLocation());
                    obj.setActiveMaterialGroupName(material.getName());
                    var instance=new QuadInstance();
                    int index=quad.materialInfo().tintIndex();
                    if (index>=0 && index<submission.tintLayers().length) instance.setColor(submission.tintLayers()[index]);
                    consumer.putBakedQuad(MeshUtils.IDENTITY_ENTRY,quad,instance);consumer.pushFace();
                }
                return obj;
            });
        }
        return activeParts;
    }
}
