package com.igrium.worldexport.entity.features;

import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.ModelParts;
import com.igrium.worldexport.replay.MaterialHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Matrix4f;
import java.util.HashMap;

/** Resolve the actual custom head-item layer, attaching sampled item transforms to the head bone. */
public final class HeadItemFeatureAdapter<S extends LivingEntityRenderState,M extends EntityModel<S> & HeadedModel> extends FeatureAdapter<S,M> {
    private final ItemMeshCapture items=new ItemMeshCapture();
    public HeadItemFeatureAdapter(RenderLayer<S,M> renderer) { super(renderer); }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static FeatureAdapter<?,?> create(RenderLayer renderer) { return new HeadItemFeatureAdapter(renderer); }

    @Override
    public void capture(CapturedEntity capture,MaterialHolder materials,S state,float limbAngle,float limbDistance,int tick) {
        if (state.wornHeadType!=null) { capture.getCaptureWarnings().add("head_skull_geometry_not_captured"); return; }
        if (state.headItem.isEmpty()) return;
        var collector=new ItemQuadCollector();
        getRenderer().submit(new PoseStack(),collector,state.lightCoords,state,limbAngle,limbDistance);
        var transforms=new HashMap<String,Matrix4f>();
        String[] head={null};
        ModelParts.forEachPart(getContextModel().root(),"root",(name,part) -> {
            String parent=capture.getParents().get(name);
            var matrix=new Matrix4f(parent==null ? new Matrix4f() : transforms.get(parent));
            matrix.translate(part.x/16,part.y/16,part.z/16).rotateZYX(part.zRot,part.yRot,part.xRot).scale(part.xScale,part.yScale,part.zScale);
            transforms.put(name,matrix);
            if (part==getContextModel().getHead()) head[0]=name;
        });
        if (head[0]==null || !capture.getCurves().containsKey(head[0])) {
            capture.getCaptureWarnings().add("head_attachment_not_found");return;
        }
        capture.getAppearance().addProperty("headAttachment",head[0]);
        items.capture(capture,materials,collector,"head/item.",head[0],new Matrix4f(transforms.get(head[0])).invert(),tick);
    }
}
