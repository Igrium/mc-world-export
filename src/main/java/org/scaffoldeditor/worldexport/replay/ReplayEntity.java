package org.scaffoldeditor.worldexport.replay;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.mat.MaterialConsumer;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModelAdapter;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModelAdapter.ModelNotFoundException;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;
import org.scaffoldeditor.worldexport.util.UtilFunctions;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

/**
 * Represents an entity in the concept of a replay export.
 */
public class ReplayEntity<T extends Entity> {
    /**
     * The base entity that this replay entity represents.
     */
    public final T entity;

    protected ReplayFile file;

    protected ReplayModelAdapter<T, ?> modelAdapter;
    protected String name;

    protected final List<Pose<?>> frames = new ArrayList<>();

    protected float startTime = 0;

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
    public ReplayModelAdapter<T, ?> genAdapter() throws ModelNotFoundException {
        if (modelAdapter != null) {
            throw new IllegalStateException("Model adapter has already been generated!");
        }

        this.modelAdapter = ReplayModelAdapter.getModelAdapter(entity);
        return modelAdapter;
    }

    public void generateMaterials(MaterialConsumer file) {
        assertModelAdapter();
        modelAdapter.generateMaterials(file);
    }

    /**
     * Get the model from this entity's model adapter. A simple wrapper for {@link ReplayModelAdapter#getModel()}.
     * @return This entityu's model.
     * @throws IllegalStateException If the model adapter hasn't been generated yet.
     */
    public ReplayModel<?> getModel() {
        assertModelAdapter();
        return modelAdapter.getModel();
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
    
    /**
     * Get the entity's model adapter.
     * @return The model adapter, or <code>null</code> if it hasn't been generated.
     */
    public ReplayModelAdapter<T, ?> getAdapter() {
        return modelAdapter;
    }

    /**
     * Capture a single frame of this entity's animation, based on the entity's current pose.
     * Should only need to be called once per-tick.
     * @param tickDelta Time since the previous tick.
     * @return The pose which has just been added to the frames list.
     */
    public Pose<?> capture(float tickDelta) {
        assertModelAdapter();

        Pose<?> pose = this.modelAdapter.getPose(tickDelta);

        this.frames.add(pose);
        return pose;
    }

    /**
     * Get the time in the file when this entity "spawns".
     * @return Start time in seconds.
     */
    public float getStartTime() {
        return startTime;
    }

    /**
     * Set the time in the file when this entity "spawns".
     * @param startTime Start time in seconds.
     */
    public void setStartTime(float startTime) {
        this.startTime = startTime;
    }

    protected void assertModelAdapter() {
        if (this.modelAdapter == null) {
            throw new IllegalStateException("Model adapter has not been generated. Generate first it with genAdapter()");
        }
    }

    /**
     * Save a replay entity out to XML.
     * @param entity Entity to save.
     * @param doc XML document.
     * @return The root <entity> tag of the XML.
     */
    public static Element writeToXML(ReplayEntity<?> entity, Document doc) {
        Element node = doc.createElement("entity");
        node.setAttribute("name", entity.getName());
        node.setAttribute("class", EntityType.getId(entity.entity.getType()).toString());

        ReplayModel<?> model = entity.getModel();

        Element modelNode = model.serialize(doc);
        node.appendChild(modelNode);

        Element animNode = doc.createElement("anim");
        animNode.setAttribute("fps", String.valueOf(entity.getFile().getFps()));
        animNode.setAttribute("start-time", String.valueOf(entity.startTime));
        StringWriter writer = new StringWriter();

        Iterator<Pose<?>> frames = entity.frames.iterator();
        int i = 0;
        while (frames.hasNext()) {
            Pose<?> pose = frames.next();
            writer.write(pose.root.toString());
            
            for (Object bone : model.getBones()) {
                Transform transform = pose.bones.get(bone);
                if (transform == null) {
                    // LogManager.getLogger().warn("Frame {} on {} is missing bone: {}.", i, entity.getName(), bone);
                    transform = i == 0 ? new Transform(false) : Transform.EMPTY; // Parts are disabled if they don't have anim on frame 1
                }

                writer.write(transform.toString(true, true, model.allowVisibility()));
            }

            if (frames.hasNext()) writer.write(System.lineSeparator());
            i++;
        }

        animNode.appendChild(doc.createTextNode(writer.toString()));
        node.appendChild(animNode);

        return node;
    }
}
