package org.scaffoldeditor.worldexport.replay;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelAdapter;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Bone;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.BoneTransform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.minecraft.entity.Entity;

/**
 * Represents an entity in the concept of a replay export.
 */
public class ReplayEntity<T extends Entity> {
    /**
     * The base entity that this replay entity represents.
     */
    public final T entity;

    public final ReplayFile file;

    protected ReplayModel model;
    protected ReplayModelAdapter<T> modelAdapter;

    protected final List<Pose> frames = new ArrayList<>();
    
    /**
     * Construct a replay entity.
     * @param entity The base entity that this replay entity represents.
     */
    public ReplayEntity(T entity, ReplayFile file) {
        this.entity = entity;
        this.file = file;
    }

    public void genAdapter() {
        this.modelAdapter = ReplayModelAdapter.getModelAdapter(entity);
        this.model = modelAdapter.generateModel(entity, file);
    }

    public ReplayModel getModel() {
        return model;
    }

    public ReplayModelAdapter<T> getAdapter() {
        return modelAdapter;
    }

    public Pose capture(float tickDelta) {
        if (this.modelAdapter == null) {
            throw new IllegalStateException("Model adapter has not been generated. Generate it with genAdapter()");
        }

        Pose pose = this.modelAdapter.getPose(entity, entity.getYaw(), tickDelta);
        this.frames.add(pose);
        return pose;
    }

    public static Element writeToXML(ReplayEntity<?> entity, Document doc) {
        Element node = doc.createElement("entity");
        Element modelNode = ReplayModel.serialize(entity.model, doc);
        node.appendChild(modelNode);

        Element animNode = doc.createElement("anim");
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
