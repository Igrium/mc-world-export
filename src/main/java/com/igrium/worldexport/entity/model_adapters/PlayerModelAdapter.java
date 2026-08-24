package com.igrium.worldexport.entity.model_adapters;

import com.igrium.worldexport.entity.CapturedEntity;
import com.igrium.worldexport.entity.LivingModelAdapter;
import com.igrium.worldexport.replay.MaterialHolder;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PlayerModelAdapter extends LivingModelAdapter<Player, AvatarRenderState, PlayerModel> {

    /**
     * @see LivingModelAdapter#fromEntityRenderer
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static PlayerModelAdapter fromRenderer(EntityRenderer<?, ?> renderer) throws ClassCastException {
        return new PlayerModelAdapter((LivingEntityRenderer) renderer);
    }

    private final Map<GameProfile, CompletableFuture<PlayerSkin>> skinLoaders = new HashMap<>();

    public PlayerModelAdapter(LivingEntityRenderer<? super Player, AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    protected @Nullable PlayerModel getModel(Player entity) {
        var skinLoader = skinLoaders.computeIfAbsent(entity.getGameProfile(), this::loadSkinAsync);
        return skinLoader.isDone() ? super.getModel(entity) : null;
    }

    private CompletableFuture<PlayerSkin> loadSkinAsync(GameProfile profile) {
        return Minecraft.getInstance().getSkinManager().get(profile)
                .thenApply(opt -> opt.orElseGet(() -> DefaultPlayerSkin.get(profile)));
    }
}
