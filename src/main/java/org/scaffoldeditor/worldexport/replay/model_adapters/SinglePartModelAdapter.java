package org.scaffoldeditor.worldexport.replay.model_adapters;

import java.util.Collections;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

public class SinglePartModelAdapter<T extends LivingEntity> extends LivingEntityModelAdapter<T, SinglePartEntityModel<T>> {

    private final Identifier texture;

    public SinglePartModelAdapter(T entity) {
        super(entity);
        this.texture = getEntityTexture(entity);
    }

    @Override
    public Identifier getTexture() {
        return texture;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected SinglePartEntityModel<T> extractModel(LivingEntityRenderer<? super T, ?> entityRenderer)
            throws ClassCastException {
        return (SinglePartEntityModel<T>) entityRenderer.getModel();
    }

    @Override
    protected void extractPartNames(SinglePartEntityModel<T> model, Map<ModelPart, String> dest) {
    }

    @Override
    protected Iterable<Pair<String, ModelPart>> getRootParts() {
        return Collections.singleton(new Pair<>("root", getEntityModel().getPart()));
    }

    private static Identifier getEntityTexture(Entity entity) {
        return MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity).getTexture(entity);
    }
    
}
