package com.igrium.worldexport.entity.model_adapters;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.igrium.worldexport.anim.AnimationCurve;
import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.ModelAdapter;
import com.igrium.worldexport.entity.features.ItemMeshCapture;
import com.igrium.worldexport.entity.features.ItemQuadCollector;
import com.igrium.worldexport.replay.MaterialHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Resolved item-display geometry and model variants for any resource-pack namespace. */
public final class ItemDisplayModelAdapter extends ModelAdapter<Display.ItemDisplay,ItemDisplayEntityRenderState> {
    private final ItemMeshCapture items=new ItemMeshCapture();
    private final DisplayRenderer.ItemDisplayRenderer displayRenderer;
    public ItemDisplayModelAdapter(DisplayRenderer.ItemDisplayRenderer renderer) { super(renderer);displayRenderer=renderer; }

    @Override
    public void capture(Display.ItemDisplay entity, ItemDisplayEntityRenderState state, CapturedEntity capture,
                        MaterialHolder materials, Vec3 offset, int tick) {
        if (state.renderState==null || state.item.isEmpty()) return;
        var billboard=state.renderState.billboardConstraints();
        capture.getAppearance().addProperty("billboard",billboard.getSerializedName());
        // Camera-facing modes are captured faithfully for the recorded camera for now.
        // Keep metadata and warn until Blender camera-relative constraints are implemented.
        if (billboard!=Display.BillboardConstraints.FIXED) capture.getCaptureWarnings().add("billboard_orientation_baked_to_replay_camera");
        float yaw=state.entityYRot,pitch=state.entityXRot;
        if (billboard==Display.BillboardConstraints.VERTICAL || billboard==Display.BillboardConstraints.CENTER) yaw=state.cameraYRot-180;
        if (billboard==Display.BillboardConstraints.HORIZONTAL || billboard==Display.BillboardConstraints.CENTER) pitch=-state.cameraXRot;
        var rotation=new Quaternionf().rotationYXZ((float)Math.toRadians(-yaw),(float)Math.toRadians(pitch),0);
        capture.addFrame(CapturedEntity.ROOT_NAME,tick,AnimationCurve.CurveFormat.POS_ROT,
                new Vector3f((float)(state.x+offset.x),(float)(state.y+offset.y),(float)(state.z+offset.z)),rotation,null);
        // Separate left and right rotations preserve affine transforms with nonuniform scale.
        Transformation transform=state.renderState.transformation().get(state.interpolationProgress);
        capture.addFrame("display/left",tick,AnimationCurve.CurveFormat.POS_ROT_SCALE,
                transform.translation(),transform.leftRotation(),transform.scale());
        capture.addFrame("display/right",tick,AnimationCurve.CurveFormat.POS_ROT,
                new Vector3f(),transform.rightRotation(),null);
        capture.getParents().put("display/right","display/left");
        var collector=new ItemQuadCollector();
        displayRenderer.submitInner(state,new PoseStack(),collector,state.lightCoords,state.interpolationProgress);
        var activeParts=items.capture(capture,materials,collector,"display/item.","display/right",new Matrix4f(),tick);
        JsonObject item=new JsonObject();
        JsonArray partNames=new JsonArray();activeParts.forEach(partNames::add);item.add("parts",partNames);
        item.addProperty("itemId",BuiltInRegistries.ITEM.getKey(entity.getItemStack().getItem()).toString());
        item.addProperty("displayContext",entity.getItemTransform().getSerializedName());
        var custom=entity.getItemStack().get(DataComponents.CUSTOM_MODEL_DATA);
        if (custom!=null) {
            var components=new JsonObject();
            JsonArray strings=new JsonArray();custom.strings().forEach(strings::add);components.add("strings",strings);
            JsonArray floats=new JsonArray();custom.floats().forEach(floats::add);components.add("floats",floats);
            JsonArray flags=new JsonArray();custom.flags().forEach(flags::add);components.add("flags",flags);
            JsonArray colors=new JsonArray();custom.colors().forEach(colors::add);components.add("colors",colors);
            item.add("customModelData",components);
        }
        // Retain selector changes without serializing unrelated/private item components.
        JsonObject selectors=capture.getAppearance().has("itemSelectors") ? capture.getAppearance().getAsJsonObject("itemSelectors") : new JsonObject();
        if (selectors.isEmpty() || !item.equals(selectors.entrySet().stream().reduce((a,b)->b).orElseThrow().getValue())) selectors.add(Integer.toString(tick),item);
        capture.getAppearance().add("itemSelectors",selectors);
    }
}
