package org.scaffoldeditor.worldexport.replay.feature_adapters;

import de.javagl.obj.ObjReader;
import de.javagl.obj.Obj;
import de.javagl.obj.Objs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.MaterialUtils;
import org.scaffoldeditor.worldexport.mat.PromisedReplayTexture;
import org.scaffoldeditor.worldexport.replay.model_adapters.BipedModelAdapter;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;

import java.io.FileNotFoundException;
import java.io.IOException;

public class ElytraFeatureAdapter implements ReplayFeatureAdapter<ReplayModelPart> {

    private static final Identifier SKIN = new Identifier("textures/entity/elytra.png");

    private final BipedModelAdapter<?> baseAdapter;

    private boolean wasElytraVisible;

    private ElytraEntityModel<PlayerEntity> elytra;

    @Nullable
    private ReplayModelPart root;
    private ReplayModelPart leftWing;
    private ReplayModelPart rightWing;

    public ElytraFeatureAdapter(BipedModelAdapter<?> baseAdapter) {
        this.baseAdapter = baseAdapter;
    }

    @Override
    public void writePose(ReplayModel.Pose<ReplayModelPart> pose, float tickDelta) {
        LivingEntity entity = baseAdapter.getEntity();
        boolean isElytraVisible = entity.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);

        if (root == null && isElytraVisible) {
            try {
                loadModel();
            } catch (IOException e) {
                // Should only happen if I fucked up the resources.
                throw new RuntimeException("Error loading resources for elytra feature adapter!", e);
            }

            baseAdapter.getBody().children.add(root);
        }

        if (!isElytraVisible) {
            if (wasElytraVisible) {
                pose.bones.put(root, Transform.INVISIBLE);
            }
            wasElytraVisible = false;
            return;
        }

        // Values taken from assetsrc/elytra.blend
        Vector3d rootPos = new Vector3d(0, 1.264, 0.308);
        Vector3d leftPos = new Vector3d(-0.248, 1.265, 0.308).sub(rootPos);
        Vector3d rightPos = new Vector3d(0.248, 1.265, 0.308).sub(rootPos);

        pose.bones.put(root, new Transform(rootPos, new Quaterniond(), new Vector3d()));
//        pose.bones.put(leftWing, new Transform(leftPos))
    }

    protected void loadModel() throws IOException {
        root = new ReplayModelPart("root");
        root.setMesh(Objs.create());
        leftWing = loadModelPart(new Identifier("worldexport:obj/elytra_left.obj"), "left-wing");
        root.children.add(leftWing);
        rightWing = loadModelPart(new Identifier("worldexport:obj/elytra_right.obj"), "right-wing");
        root.children.add(rightWing);
    }

    protected ReplayModelPart loadModelPart(Identifier location, String name) throws  IOException {
        var resource = MinecraftClient.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty())
            throw new FileNotFoundException(location.toString());

        try (var in = resource.get().getInputStream()) {
            ReplayModelPart part = new ReplayModelPart(name);
            Obj obj = ObjReader.read(in);
            part.setMesh(obj);
            return part;
        }
    }

    @Override
    public void generateMaterials(MaterialConsumer file) {
        if (root != null) {
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
