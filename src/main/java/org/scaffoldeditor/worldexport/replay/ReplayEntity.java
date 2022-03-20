package org.scaffoldeditor.worldexport.replay;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3d;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Bone;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.BoneTransform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelAdapter.ModelNotFoundException;
import org.scaffoldeditor.worldexport.util.UtilFunctions;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelAdapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;

/**
 * Represents an entity in the concept of a replay export.
 */
public class ReplayEntity<T extends Entity> {
    /**
     * The base entity that this replay entity represents.
     */
    public final T entity;

    protected ReplayFile file;

    protected ReplayModel model;
    protected ReplayModelAdapter<T> modelAdapter;
    protected String name;

    protected final List<Pose> frames = new ArrayList<>();

    private MinecraftClient client = MinecraftClient.getInstance();

    /**
     * Construct a replay entity with a default name.
     * @param entity The base entity that this replay entity represents.
     * @param file The replay file this entity belongs to.
     */
    public ReplayEntity(T entity, ReplayFile file) {
        String name = entity.getEntityName();
        if (name.equals(entity.getUuidAsString())) {
            if (entity.hasCustomName()) {
                name = entity.getCustomName().asString();
            } else {
                name = EntityType.getId(entity.getType()).toUnderscoreSeparatedString();
            }
        }

        this.entity = entity;
        this.file = file;

        this.name = UtilFunctions.validateName(name, UtilFunctions.nameView(file.entities));
    }
    
    /**
     * Construct a replay entity.
     * @param entity The base entity that this replay entity represents.
     * @param file The replay file this entity belongs to.
     * @param name Name to give the entity in the file.
     */
    public ReplayEntity(T entity, ReplayFile file, String name) {
        this.entity = entity;
        this.file = file;

        this.name = UtilFunctions.validateName(name, UtilFunctions.nameView(file.entities));
    }

    /**
     * Generate this entity's model adapter.
     * @throws ModelNotFoundException If the entity type does not have a model adapter factory.
     */
    public ReplayModelAdapter<T> genAdapter() throws ModelNotFoundException {
        this.modelAdapter = ReplayModelAdapter.getModelAdapter(entity);
        this.model = modelAdapter.generateModel(entity, file);
        modelAdapter.generateMaterials(entity, file);
        
        return modelAdapter;
    }

    public ReplayModel getModel() {
        return model;
    }

    /**
     * Get the replay file this entity belongs to.
     * @return The replay file.
     */
    public ReplayFile getFile() {
        return file;
    }

    /**
     * Get the name this entity will serialize with.
     * @return Entity name.
     */
    public String getName() {
        return name;
    }

    public ReplayModelAdapter<T> getAdapter() {
        return modelAdapter;
    }

    public Pose capture(float tickDelta) {
        if (this.modelAdapter == null) {
            throw new IllegalStateException("Model adapter has not been generated. Generate it with genAdapter()");
        }
        EntityRenderer<? super T> renderer = client.getEntityRenderDispatcher().getRenderer(entity);
        Vec3d mcPos = entity.getPos();
        mcPos = mcPos.add(renderer.getPositionOffset(entity, tickDelta));

        Pose pose = this.modelAdapter.getPose(entity, entity.getYaw(), tickDelta);
        pose.pos = pose.pos.add(new Vector3d(mcPos.x, mcPos.y, mcPos.z), new Vector3d());

        this.frames.add(pose);
        return pose;
    }

    public static Element writeToXML(ReplayEntity<?> entity, Document doc) {
        Element node = doc.createElement("entity");
        node.setAttribute("name", entity.getName());
        Element modelNode = ReplayModel.serialize(entity.model, doc);
        node.appendChild(modelNode);

        Element animNode = doc.createElement("anim");
        animNode.setAttribute("fps", String.valueOf(entity.getFile().getFps()));
        StringWriter writer = new StringWriter();

        Iterator<Pose> frames = entity.frames.iterator();
        while (frames.hasNext()) {
            Pose pose = frames.next();
            // Make a list of bone transforms in definition order.
            List<BoneTransform> transforms = new ArrayList<>();
            for (Bone bone : entity.model.getBones()) {
                BoneTransform transform = pose.bones.get(bone);
                if (transform != null) transforms.add(transform);
            }
            
            // Root pos
            List<String> rootVals = new ArrayList<>();
            rootVals.add(String.valueOf(pose.rot.w()));
            rootVals.add(String.valueOf(pose.rot.x()));
            rootVals.add(String.valueOf(pose.rot.y()));
            rootVals.add(String.valueOf(pose.rot.z()));

            rootVals.add(String.valueOf(pose.pos.x()));
            rootVals.add(String.valueOf(pose.pos.y()));
            rootVals.add(String.valueOf(pose.pos.z()));

            writer.append(String.join(" ", rootVals));
            writer.append("; ");

            Iterator<BoneTransform> bones = transforms.iterator();
            while (bones.hasNext()) {
                BoneTransform bone = bones.next();
                List<String> vals = new ArrayList<>();
   
                vals.add(String.valueOf(bone.rotation.w()));
                vals.add(String.valueOf(bone.rotation.x()));
                vals.add(String.valueOf(bone.rotation.y()));
                vals.add(String.valueOf(bone.rotation.z()));
   
                vals.add(String.valueOf(bone.translation.x()));
                vals.add(String.valueOf(bone.translation.y()));
                vals.add(String.valueOf(bone.translation.z()));
   
                vals.add(String.valueOf(bone.scale.x()));
                vals.add(String.valueOf(bone.scale.y()));
                vals.add(String.valueOf(bone.scale.z()));
                
                writer.append(String.join(" ", vals));
                writer.append(';');
   
                if (bones.hasNext()) writer.append(' ');
            }
            if (frames.hasNext()) writer.write("\n");
        }
        writer.flush();
        animNode.appendChild(doc.createTextNode(writer.toString()));
        node.appendChild(animNode);

        return node;
    }
}
