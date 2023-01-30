package org.scaffoldeditor.worldexport.replay.model_adapters;

import org.scaffoldeditor.worldexport.replay.model_adapters.BipedModelAdapter.BipedModelFactory;
import org.scaffoldeditor.worldexport.replay.model_adapters.ReplayModelAdapter.ReplayModelAdapterFactory;
import org.scaffoldeditor.worldexport.replay.model_adapters.custom.FireballModelAdapter;
import org.scaffoldeditor.worldexport.replay.model_adapters.custom.ProjectileModelAdapter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.projectile.AbstractFireballEntity;
import net.minecraft.util.Identifier;

/**
 * Contains replay models for vanilla Minecraft entities.
 */
public final class ReplayModels {
    private ReplayModels() {
    };

    public static final float BIPED_Y_OFFSET = 1.5f;
    public static final float QUADRUPED_Y_OFFSET = 1.5f;
    static MinecraftClient client = MinecraftClient.getInstance();


    public static class AnimalModelFactory<T extends LivingEntity> implements ReplayModelAdapterFactory<T> {

        public Identifier tex;
        public float y_offset;

        public AnimalModelFactory(Identifier tex, float y_offset) {
            this.tex = tex;
            this.y_offset = y_offset;
        }

        @Override
        public AnimalModelAdapter<T> create(T entity) {
            return new AnimalModelAdapter<T>(entity, tex, y_offset);
        }

    }

    public static class SinglePartModelFactory implements ReplayModelAdapterFactory<LivingEntity> {

        @Override
        public ReplayModelAdapter<?> create(LivingEntity entity) {
            return new SinglePartModelAdapter<LivingEntity>(entity);
        }
          
    }

    @SuppressWarnings("rawtypes")
    public static void registerDefaults() {

        ReplayModelAdapter.REGISTRY.put(new Identifier("player"), entity -> PlayerModelAdapter.newInstance((AbstractClientPlayerEntity) entity));

        /**
         * QUADRIPEDS
         */
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:cow"),
                new AnimalModelFactory(new Identifier("textures/entity/cow/cow.png"), QUADRUPED_Y_OFFSET));
                
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:goat"),
                new AnimalModelFactory(new Identifier("textures/entity/goat/goat.png"), QUADRUPED_Y_OFFSET));

        // TODO: write custom model adapter that updates texture situationally.
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:panda"), 
                new AnimalModelFactory(new Identifier("textures/entity/panda/panda.png"), QUADRUPED_Y_OFFSET));
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:pig"),
                new AnimalModelFactory(new Identifier("textures/entity/pig/pig.png"), QUADRUPED_Y_OFFSET));
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:polar_bear"),
                new AnimalModelFactory(new Identifier("textures/entity/bear/polarbear.png"), QUADRUPED_Y_OFFSET));
        
        // TODO: Make this render wool properly.
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:sheep"),
                new AnimalModelFactory(new Identifier("textures/entity/sheep/sheep.png"), QUADRUPED_Y_OFFSET));
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:turtle"), 
                new AnimalModelFactory(new Identifier("textures/entity/turtle/big_sea_turtle.png"), QUADRUPED_Y_OFFSET));
            
        /**
         * BIPEDS
         */
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:zombie"),
                new BipedModelFactory(new Identifier("textures/entity/zombie/zombie.png")));
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:drowned"),
                new BipedModelFactory(new Identifier("textures/entity/zombie/drowned.png")));

        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:enderman"), 
                new AnimalModelFactory(new Identifier("textures/entity/enderman/enderman.png"), BIPED_Y_OFFSET));
            
        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:skeleton"),
                new BipedModelFactory(new Identifier("textures/entity/skeleton/skeleton.png")));

        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:wither_skeleton"), 
                new BipedModelFactory(new Identifier("textures/entity/skeleton/wither_skeleton.png")));

        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:stray"), 
                new BipedModelFactory(new Identifier("textures/entity/skeleton/stray.png")));

        ReplayModelAdapter.REGISTRY.put(new Identifier("minecraft:vex"), 
                new BipedModelFactory(new Identifier("textures/entity/illager/vex.png")));
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("zombie_villager"), 
                new BipedModelFactory(new Identifier("textures/entity/zombie_villager/zombie_villager.png")));

        ReplayModelAdapter.REGISTRY.put(new Identifier("piglin"),
                new BipedModelFactory(new Identifier("textures/entity/piglin/piglin.png")));

        ReplayModelAdapter.REGISTRY.put(new Identifier("piglin_brute"),
                new BipedModelFactory(new Identifier("textures/entity/piglin/piglin_brute.png")));

        ReplayModelAdapter.REGISTRY.put(new Identifier("zombified_piglin"),
                new BipedModelFactory(new Identifier("textures/entity/piglin/zombified_piglin.png")));

        /**
         * MISC
         */

        // TODO: Axolotl's varients make implementation non-trivial

        ReplayModelAdapter.REGISTRY.put(new Identifier("bee"),
                new AnimalModelFactory(new Identifier("textures/entity/bee/bee.png"), QUADRUPED_Y_OFFSET));

        ReplayModelAdapter.REGISTRY.put(new Identifier("chicken"), entity -> new ChickenModelAdapter((ChickenEntity) entity));

        // ReplayModelAdapter.REGISTRY.put(new Identifier("chicken"), entity -> new ChickenModelAdapter(entity));
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("fox"),
                new AnimalModelFactory(new Identifier("textures/entity/fox/fox.png"), QUADRUPED_Y_OFFSET));
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("hoglin"), 
                new AnimalModelFactory(new Identifier("textures/entity/hoglin/hoglin.png"), QUADRUPED_Y_OFFSET));

        ReplayModelAdapter.REGISTRY.put(new Identifier("horse"), ent -> new HorseModelAdapter((HorseEntity) ent));
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("donkey"),
                new AnimalModelFactory(new Identifier("textures/entity/horse/donkey.png"), QUADRUPED_Y_OFFSET));
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("item"), ent -> new ItemModelAdapter((ItemEntity) ent));
        
        /**
         * SINGLE PART
         */
        registerSinglePart("creeper");
        registerSinglePart("illager");
        registerSinglePart("wither");
        registerSinglePart("magma_cube");
        registerSinglePart("parrot");
        registerSinglePart("dolphin");
        registerSinglePart("villager");
        registerSinglePart("salmon");
        registerSinglePart("spider");
        registerSinglePart("phantom");
        registerSinglePart("shulker");
        registerSinglePart("ghast");
        registerSinglePart("strider");
        registerSinglePart("ravager");
        registerSinglePart("silverfish");
        registerSinglePart("guardian");
        registerSinglePart("snow_golem");
        registerSinglePart("slime");
        registerSinglePart("iron_golem");
        registerSinglePart("cod");
        registerSinglePart("bat");
        registerSinglePart("endermite");
        registerSinglePart("blaze");

        /**
         * CUSTOM
         */
        ReplayModelAdapter.REGISTRY.put(new Identifier("fireball"),
                        entity -> new FireballModelAdapter((AbstractFireballEntity) entity));
        
        ReplayModelAdapter.REGISTRY.put(new Identifier("arrow"), ProjectileModelAdapter::new);
        ReplayModelAdapter.REGISTRY.put(new Identifier("spectral_arrow"), ProjectileModelAdapter::new);
    }

    /**
     * Register a single part entity model. Cuts down on the typing.
     */
    private static void registerSinglePart(Identifier id) {
        ReplayModelAdapter.REGISTRY.put(id, new SinglePartModelFactory());
    }

    private static void registerSinglePart(String id) {
        registerSinglePart(new Identifier(id));
    }
}
