package org.scaffoldeditor.worldexport.replay.models;

import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.Material.Field;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.util.MeshUtils;
import org.scaffoldeditor.worldexport.vcap.ObjVertexConsumer;

import de.javagl.obj.Obj;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

public class ReplayItemRenderer {

    public static final Material ITEM_MAT;

    static {
        ITEM_MAT = new Material();
        ITEM_MAT.color = new Field("world"); // Should be provided by Vcap
        ITEM_MAT.roughness = new Field(1);
        ITEM_MAT.transparent = true;
    }

    public static void renderItem(ItemStack stack, Mode renderMode, boolean leftHanded, MatrixStack matrices, Obj obj, BakedModel model, MaterialConsumer materials) {
        if (stack.isEmpty()) return;

        // Add material
        materials.addMaterial("item", ITEM_MAT);
        obj.setActiveMaterialGroupName("item");

        VertexConsumerProvider vertices = MeshUtils.wrapVertexConsumer(new ObjVertexConsumer(obj));
        MinecraftClient.getInstance().getItemRenderer().renderItem(stack, renderMode, leftHanded, matrices, vertices, 255, 0, model);
    }
}
