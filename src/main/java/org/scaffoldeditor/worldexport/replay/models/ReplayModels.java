package org.scaffoldeditor.worldexport.replay.models;

import org.scaffoldeditor.worldexport.replay.models.ReplayModelAdapter.ReplayModelAdapterFactory;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PigEntityModel;
import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.util.Identifier;

/**
 * Contains replay models for vanilla Minecraft entities.
 */
public final class ReplayModels {
    private ReplayModels() {
    };

    public static final float BIPED_Y_OFFSET = 1.5f;
    public static final float QUADRUPED_Y_OFFSET = 1.5f;

    public static void registerDefaults() {
        MinecraftClient client = MinecraftClient.getInstance();
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:skeleton"),
                new ReplayModelAdapterFactory<SkeletonEntity>() {

                    @Override
                    @SuppressWarnings("unchecked")
                    public ReplayModelAdapter<SkeletonEntity> create(SkeletonEntity entity) {
                        LivingEntityRenderer<SkeletonEntity, SkeletonEntityModel<SkeletonEntity>> renderer = (LivingEntityRenderer<SkeletonEntity, SkeletonEntityModel<SkeletonEntity>>) client
                                .getEntityRenderDispatcher().getRenderer(entity);

                        return new AnimalModelWrapper<>(renderer.getModel(), BIPED_Y_OFFSET);
                    }

                });
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:pig"),
                new ReplayModelAdapterFactory<PigEntity>() {

                    @Override
                    @SuppressWarnings("unchecked")
                    public ReplayModelAdapter<PigEntity> create(PigEntity entity) {
                        LivingEntityRenderer<PigEntity, PigEntityModel<PigEntity>> renderer = (LivingEntityRenderer<PigEntity, PigEntityModel<PigEntity>>) client
                                .getEntityRenderDispatcher().getRenderer(entity);

                        return new AnimalModelWrapper<>(renderer.getModel(), QUADRUPED_Y_OFFSET);
                    }

                });

    }
}
