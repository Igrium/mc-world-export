package org.scaffoldeditor.worldexport.replay.model_adapters.custom;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.MaterialUtils;
import org.scaffoldeditor.worldexport.mat.PromisedReplayTexture;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModelAdapter;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import de.javagl.obj.ObjReader;
import net.minecraft.client.render.entity.ArrowEntityRenderer;
import net.minecraft.client.render.entity.ProjectileEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.math.MathHelper;

/**
 * A model adapter for arrows (and their subclasses.)
 * @see ProjectileEntityRenderer
 */
public class ProjectileModelAdapter implements ReplayModelAdapter<MultipartReplayModel> {

    private final PersistentProjectileEntity entity;
    private MultipartReplayModel model;

    private ReplayModelPart root;

    public static final String MAT_NAME = "minecraft/textures/entity/projectiles/arrow";

    public ProjectileModelAdapter(PersistentProjectileEntity entity) {
        this.entity = entity;
        try {
            loadModel();
        } catch (IOException e) {
            throw new RuntimeException("Error loading resources for arrow model adapter!", e);
        }
    }

    public ProjectileModelAdapter(Entity entity) {
        this((PersistentProjectileEntity) entity);
    }

    protected void loadModel() throws IOException {
        model = new MultipartReplayModel();
        root = new ReplayModelPart("root");
        model.bones.add(root);

        InputStream in = new BufferedInputStream(getClass().getResourceAsStream("/assets/worldexport/obj/arrow.obj"));
        root.setMesh(ObjReader.read(in));
        in.close();
    }

    public PersistentProjectileEntity getEntity() {
        return entity;
    }

    @Override
    public MultipartReplayModel getModel() {
        return model;
    }

    @Override
    public void generateMaterials(MaterialConsumer file) {
        PromisedReplayTexture tex = new PromisedReplayTexture(ArrowEntityRenderer.TEXTURE);
        String texName = MaterialUtils.getTexName(ArrowEntityRenderer.TEXTURE);

        Material mat = new Material();
        mat.setRoughness(1f);
        mat.setColor(texName);

        file.addMaterial(MAT_NAME, mat);
        file.addTexture(texName, tex);
    }

    @Override
    public Pose<ReplayModelPart> getPose(float tickDelta) {
        Pose<ReplayModelPart> pose = new Pose<>();

        Vector3d pos = new Vector3d(entity.getPos().getX(), entity.getPos().getY(), entity.getPos().getZ());

        float yaw = MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw());
        float pitch = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());
        Quaterniond rot = new Quaterniond().rotateY(Math.toRadians(yaw - 90d)).rotateZ(Math.toRadians(pitch));

        Transform root = new Transform(pos, rot, new Vector3d(1));
        pose.root = root;
        
        pose.bones.put(this.root, Transform.NEUTRAL);


        return pose;
    }
}
