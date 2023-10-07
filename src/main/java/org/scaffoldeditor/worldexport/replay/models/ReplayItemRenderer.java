package org.scaffoldeditor.worldexport.replay.models;

import net.minecraft.client.render.model.json.ModelTransformationMode;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.PromisedReplayTexture;
import org.scaffoldeditor.worldexport.mat.TextureExtractor;
import org.scaffoldeditor.worldexport.vcap.ObjVertexConsumer;
import org.scaffoldeditor.worldexport.vcap.WrappedVertexConsumerProvider;

import com.google.common.collect.ImmutableSet;

import de.javagl.obj.Obj;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ReplayItemRenderer {

    public static final Material ITEM_MAT = new Material().setColor("world").setRoughness(1).setTransparent(true);
    public static final Material SHEID_MAT = new Material().setColor("shield").setRoughness(1).setTransparent(true);

    public static void renderItem(ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, Obj obj, BakedModel model, MaterialConsumer materials) {
        renderItem(stack, renderMode, leftHanded, matrices, obj, model);
        addMaterials(materials);
    }

    public static void renderItem(ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded, MatrixStack matrices, Obj obj, BakedModel model) {
        if (stack.isEmpty()) return;

        if (stack.isOf(Items.SHIELD)) {
            obj.setActiveMaterialGroupName("shield");
        } else {
            obj.setActiveMaterialGroupName("item");
        }
        
        // VertexConsumerProvider vertices = MeshUtils.wrapVertexConsumer(new ObjVertexConsumer(obj));
        VertexConsumerProvider vertices = new WrappedVertexConsumerProvider(new ObjVertexConsumer(obj), null,
                ImmutableSet.of(
                        RenderLayer.getDirectGlint(),
                        RenderLayer.getDirectEntityGlint()));

        MinecraftClient.getInstance().getItemRenderer().renderItem(stack, renderMode, leftHanded, matrices, vertices,
                255, 0, model);
    }

    /**
     * Add the materials required to render items to a material consumer.
     * @param materials Material consumer to add to.
     */
    public static void addMaterials(MaterialConsumer materials) {

        materials.addMaterial("item", ITEM_MAT);
        
        if (!materials.hasTexture("world")) {
            materials.addTexture("world", new PromisedReplayTexture(TextureExtractor.getAtlasTexture()));
        }

        materials.addMaterial("shield", SHEID_MAT);

        if (!materials.hasTexture("shield")) {
            materials.addTexture("shield", new PromisedReplayTexture(TextureExtractor.getAtlasTexture(TexturedRenderLayers.SHIELD_PATTERNS_ATLAS_TEXTURE)));
        }
    }
}
