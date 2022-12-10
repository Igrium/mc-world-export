package org.scaffoldeditor.worldexport.replaymod.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;

import net.minecraft.util.Identifier;

public class GuiTrashButton extends GuiIconButton<GuiTrashButton> {

    public static final Identifier TEXTURE = new Identifier("worldexport", "icons/trash.png");
    protected static final ReadableDimension TEXTURE_SIZE = new Dimension(48, 16);
    protected static final ReadableDimension SPRITE_SIZE = new Dimension(16, 16);

    public GuiTrashButton() {}

    public GuiTrashButton(GuiContainer<?> container) {
        super(container);
    }

    @Override
    protected Identifier getTexture() {
        return TEXTURE;
    }

    @Override
    protected ReadableDimension getTextureSize() {
        return TEXTURE_SIZE;
    }

    @Override
    protected ReadableDimension getSpriteSize() {
        return SPRITE_SIZE;
    }

    @Override
    protected ReadablePoint getSpriteUV(ButtonState state) {
        if (state == ButtonState.HOVER) {
            return new Point(16, 0);
        } else if (state == ButtonState.DISABLED) {
            return new Point(32, 0);
        } else {
            return new Point(0, 0);
        }
    }

    @Override
    protected GuiTrashButton getThis() {
        return this;
    }
    
}
