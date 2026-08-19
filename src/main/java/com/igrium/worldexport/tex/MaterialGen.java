package com.igrium.worldexport.tex;

import com.igrium.worldexport.replay.MaterialHolder;
import com.mojang.blaze3d.platform.NativeImage;
import de.javagl.obj.Mtl;
import de.javagl.obj.Mtls;
import lombok.experimental.UtilityClass;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;

import java.util.concurrent.CompletableFuture;

@UtilityClass
public final class MaterialGen {
    public String getAnimatedTex(MaterialHolder materials, String mtlLibName, SpriteContents sprite) {
        Identifier spriteName = sprite.name();
        String matName = "spritesheet." + spriteName.getNamespace() + "." + spriteName.getPath();
        Identifier texId = SpriteSource.TEXTURE_ID_CONVERTER.idToFile(sprite.name());

        materials.getOrCreateMtl(mtlLibName, matName, n -> {

            Mtl mtl = Mtls.create(matName);
            mtl.setMapKd(matName);
            mtl.setMapD(matName);

            materials.getTextures().computeIfAbsent(matName, _ -> buildSpritesheet(sprite));

            ReplayMtl mat = new ReplayMtl(mtl);
            mat.properties().put("spritesheet", ReplayMtl.Property.of(true));

            return mat;
        });

        return matName;
    }

    /**
     * Build a vertical spritesheet from an animated texture
     *
     * @param sprite Sprite contents of the base texture
     * @return The vertical spritesheet
     * @apiNote We can't take the spritesheet directly from disk because it might use a different layout than what
     * Replay Exporter wants
     */
    public CompletableFuture<ManagedNativeImage> buildSpritesheet(SpriteContents sprite) {
        // Extract variables so we're not referencing sprite in lambda
        int frameWidth = sprite.width();
        int frameHeight = sprite.height();
        int[] frames = sprite.getUniqueFrames().toIntArray();
        boolean isAnimated = sprite.isAnimated();

        Identifier textureId = SpriteSource.TEXTURE_ID_CONVERTER.idToFile(sprite.name());
        return TextureExtractor.pullTextureAsync(textureId).thenApply(source -> {
            if (!isAnimated) {
                return source;
            }

            int frameRowSize = Math.max(source.getWidth() / frameWidth, 1);

            return source.useRawImage(img -> {
                NativeImage dest = new NativeImage(img.format(), frameWidth, frameHeight * frames.length, false);
                for (int i = 0; i < frames.length; i++) {
                    int frame = frames[i];
                    int offsetX = (frame % frameRowSize) * frameWidth;
                    int offsetY = (frame / frameRowSize) * frameHeight;

                    int destX = 0;
                    int destY = i * frameHeight;

                    img.copyRect(dest, offsetX, offsetY, destX, destY, frameWidth, frameHeight, false, false);
                }
                return ManagedNativeImage.of(dest);
            });
        });
    }


    public static String getTexturePath(Identifier id) {
        String path = id.getPath();
        if (!path.endsWith(".png"))
            path += ".png";
        return id.getNamespace() + "/" + path;
    }
}
