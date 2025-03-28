package org.scaffoldeditor.worldexport.replay.feature_adapters;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.joml.Matrix4d;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.MaterialUtils;
import org.scaffoldeditor.worldexport.mat.PromisedReplayTexture;
import org.scaffoldeditor.worldexport.mixins.ElytraEntityModelAccessor;
import org.scaffoldeditor.worldexport.replay.model_adapters.BipedModelAdapter;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.util.MeshUtils;
import org.scaffoldeditor.worldexport.util.ModelUtils;

import java.io.IOException;

public class ElytraFeatureAdapter implements ReplayFeatureAdapter<ReplayModelPart> {

    public static final int ELYTRA_TICK_MULTIPLIER = 3; // Emulate 60 fps
    private static final Identifier SKIN = new Identifier("textures/entity/elytra.png");

    private final BipedModelAdapter<? extends LivingEntity> baseAdapter;

    private boolean wasElytraVisible;

    private ElytraEntityModel<LivingEntity> elytra;
    private ModelPart leftModelPart;
    private ModelPart rightModelPart;

    private ReplayModelPart leftWing;
    private ReplayModelPart rightWing;

    public ElytraFeatureAdapter(BipedModelAdapter<? extends LivingEntity> baseAdapter) {
        this.baseAdapter = baseAdapter;
        TexturedModelData elytraModelData = ElytraEntityModel.getTexturedModelData();
        elytra = new ElytraEntityModel<>(elytraModelData.createModel());
        leftModelPart = ((ElytraEntityModelAccessor) elytra).getLeftWing();
        rightModelPart = ((ElytraEntityModelAccessor) elytra).getRightWing();
    }

    public void animateModel(float limbAngle, float limbDistance, float tickDelta) {
        elytra.animateModel(baseAdapter.getEntity(), limbAngle, limbDistance, tickDelta);
    }

    public void setAngles(float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        // For some REALLY DUMB reason, elytra animation is based on your client's FRAME RATE!?!?!?!?!?
        // Run the animation tick multiple times to account for 20fps export
        for (int i = 0; i < ELYTRA_TICK_MULTIPLIER; i++) {
            elytra.setAngles(baseAdapter.getEntity(), limbAngle, limbDistance, age, headYaw, headPitch);
        }
    }

    @Override
    public void writePose(ReplayModel.Pose<ReplayModelPart> pose, float tickDelta) {
        LivingEntity entity = baseAdapter.getEntity();
        boolean isElytraVisible = entity.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);

        if (leftWing == null && isElytraVisible) {
            try {
                loadModel();
            } catch (IOException e) {
                // Should only happen if I fucked up the resources.
                throw new RuntimeException("Error loading resources for elytra feature adapter!", e);
            }

            baseAdapter.getBody().children.add(leftWing);
            baseAdapter.getBody().children.add(rightWing);
        }

        if (!isElytraVisible) {
            if (wasElytraVisible) {
                pose.bones.put(leftWing, Transform.INVISIBLE);
                pose.bones.put(rightWing, Transform.INVISIBLE);
            }
            wasElytraVisible = false;
            return;
        }

        var leftTransform = ModelUtils.getPartTransform(leftModelPart, new Matrix4d());
        var rightTransform = ModelUtils.getPartTransform(rightModelPart, new Matrix4d());

        pose.bones.put(leftWing, new Transform(leftTransform));
        pose.bones.put(rightWing, new Transform(rightTransform));

        wasElytraVisible = true;
    }

    protected void loadModel() throws IOException {

        String matName = MaterialUtils.getTexName(SKIN);

        leftWing = new ReplayModelPart("left-wing");
        leftWing.getMesh().setActiveMaterialGroupName(matName);
        MeshUtils.appendModelPart(leftModelPart, leftWing.getMesh(), false, null);

        rightWing = new ReplayModelPart("right-wing");
        rightWing.getMesh().setActiveMaterialGroupName(matName);
        MeshUtils.appendModelPart(rightModelPart, rightWing.getMesh(), false, null);

    }

    @Override
    public void generateMaterials(MaterialConsumer file) {
        if (leftWing != null) {
            PromisedReplayTexture texture = new PromisedReplayTexture(SKIN);
            String texName = MaterialUtils.getTexName(SKIN);

            Material mat = new Material();
            mat.setRoughness(1f);
            mat.setColor(texName);

            file.addMaterial(texName, mat);
            file.addTexture(texName, texture);
        }
    }
}
