package org.scaffoldeditor.worldexport.replay.model_adapters.custom;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.mat.Material;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.mat.URLReplayTexture;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModelAdapter;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import de.javagl.obj.ObjReader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.AbstractFireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;

public class FireballModelAdapter implements ReplayModelAdapter<MultipartReplayModel> {

    private final AbstractFireballEntity entity;
    private MultipartReplayModel model;

    private ReplayModelPart root;
    private boolean small;

    private final String TEX_NAME = "custom/fireball";

    public FireballModelAdapter(AbstractFireballEntity entity) {
        this.entity = entity;
        setSmall(entity instanceof SmallFireballEntity);
        try {
            loadModel();
        } catch (IOException e) {
            throw new RuntimeException("Error loading resources for fireball model adapter!", e);
        }
    }

    public FireballModelAdapter(Entity entity) throws ClassCastException {
        this((AbstractFireballEntity) entity);
    }

    protected void loadModel() throws IOException {
        model = new MultipartReplayModel();
        root = new ReplayModelPart("root");
        model.bones.add(root);

        InputStream in = new BufferedInputStream(getClass().getResourceAsStream("/assets/worldexport/obj/fireball.obj"));
        root.setMesh(ObjReader.read(in));
        in.close();
    }

    public AbstractFireballEntity getEntity() {
        return entity;
    }

    @Override
    public MultipartReplayModel getModel() {
        return model;
    }

    public final boolean isSmall() {
        return small;
    }

    public void setSmall(boolean small) {
        this.small = small;
    }

    @Override
    public void generateMaterials(MaterialConsumer file) {
        URLReplayTexture tex = new URLReplayTexture(
                getClass().getResource("/assets/worldexport/replaytextures/fireball_base_color.png"));

        Material mat = new Material();
        mat.setRoughness(.7f);
        mat.setColor(TEX_NAME);

        file.addMaterial(TEX_NAME, mat);
        file.addTexture(TEX_NAME, tex);
    }

    @Override
    public Pose<ReplayModelPart> getPose(float tickDelta) {
        Pose<ReplayModelPart> pose = new Pose<>();

        Vector3d pos = new Vector3d(entity.getPos().getX(), entity.getPos().getY(), entity.getPos().getZ());
        Transform root = new Transform(pos, new Quaterniond(), new Vector3d(small ? .5 : 1.5));
        pose.root = root;

        pose.bones.put(this.root, Transform.NEUTRAL);


        return pose;
    }
    
}
