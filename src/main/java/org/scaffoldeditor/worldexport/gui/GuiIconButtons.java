package org.scaffoldeditor.worldexport.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;

import net.minecraft.util.Identifier;

public final class GuiIconButtons {
    private GuiIconButtons() {};

    public static GuiIconButton<?> create(Identifier texture, ReadableDimension textureSize,
            ReadableDimension spriteSize, ReadablePoint defaultUV, ReadablePoint hoverUV, ReadablePoint disabledUV) {
        return new Impl(texture, textureSize, spriteSize, defaultUV, hoverUV, disabledUV);
    }
    
    public static GuiIconButton<?> create(GuiContainer<?> container, Identifier texture, ReadableDimension textureSize,
            ReadableDimension spriteSize, ReadablePoint defaultUV, ReadablePoint hoverUV, ReadablePoint disabledUV) {
        return new Impl(container, texture, textureSize, spriteSize, defaultUV, hoverUV, disabledUV);
    }

    public static final IconFactory TRASH = new FactoryImpl(new Identifier("worldexport", "icons/trash.png"),
            new Dimension(48, 16), new Dimension(16, 16), new Point(0, 0), new Point(16, 0), new Point(32, 0));
    
    public static final IconFactory PENCIL = new FactoryImpl(new Identifier("worldexport", "icons/pencil.png"),
    new Dimension(48, 16), new Dimension(16, 16), new Point(0, 0), new Point(16, 0), new Point(32, 0));

    public static interface IconFactory {
        GuiIconButton<?> create();
        GuiIconButton<?> create(GuiContainer<?> container);
    }

    private static class FactoryImpl implements IconFactory {

        public final Identifier texture;
        public final ReadableDimension textureSize;
        public final ReadableDimension spriteSize;

        public final ReadablePoint defaultUV;
        public final ReadablePoint hoverUV;
        public final ReadablePoint disabledUV;

        public FactoryImpl(Identifier texture, ReadableDimension textureSize, ReadableDimension spriteSize,
        ReadablePoint defaultUV, ReadablePoint hoverUV, ReadablePoint disabledUV) {
            this.texture = texture;
            this.textureSize = textureSize;
            this.spriteSize = spriteSize;
            this.defaultUV = defaultUV;
            this.hoverUV = hoverUV;
            this.disabledUV = disabledUV;
        }

        @Override
        public GuiIconButton<?> create() {
            return GuiIconButtons.create(texture, textureSize, spriteSize, defaultUV, hoverUV, disabledUV);
        }

        @Override
        public GuiIconButton<?> create(GuiContainer<?> container) {
            return GuiIconButtons.create(container, texture, textureSize, spriteSize, defaultUV, hoverUV, disabledUV);
        }
        
    }

    private static class Impl extends GuiIconButton<Impl> {

        public final Identifier texture;
        public final ReadableDimension textureSize;
        public final ReadableDimension spriteSize;

        public final ReadablePoint defaultUV;
        public final ReadablePoint hoverUV;
        public final ReadablePoint disabledUV;

        public Impl(Identifier texture, ReadableDimension textureSize, ReadableDimension spriteSize,
                ReadablePoint defaultUV, ReadablePoint hoverUV, ReadablePoint disabledUV) {
            this.texture = texture;
            this.textureSize = textureSize;
            this.spriteSize = spriteSize;
            this.defaultUV = defaultUV;
            this.hoverUV = hoverUV;
            this.disabledUV = disabledUV;
        }

        public Impl(GuiContainer<?> container, Identifier texture, ReadableDimension textureSize, ReadableDimension spriteSize,
        ReadablePoint defaultUV, ReadablePoint hoverUV, ReadablePoint disabledUV) {
            super(container);
            this.texture = texture;
            this.textureSize = textureSize;
            this.spriteSize = spriteSize;
            this.defaultUV = defaultUV;
            this.hoverUV = hoverUV;
            this.disabledUV = disabledUV;
        }

        @Override
        protected Identifier getTexture() {
            return texture;
        }

        @Override
        protected ReadableDimension getTextureSize() {
            return textureSize;
        }

        @Override
        protected ReadableDimension getSpriteSize() {
            return spriteSize;
        }

        @Override
        protected ReadablePoint getSpriteUV(ButtonState state) {
            if (state == ButtonState.DISABLED) {
                return disabledUV;
            } else if (state == ButtonState.HOVER) {
                return hoverUV;
            } else {
                return defaultUV;
            }
        }

        @Override
        protected Impl getThis() {
            return this;
        }
    }
}
