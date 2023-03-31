package org.scaffoldeditor.worldexport.gui;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiClickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public abstract class GuiIconButton<T extends GuiIconButton<T>> extends AbstractGuiClickable<T> {

    public static enum ButtonState {
        DEFAULT,
        HOVER,
        DISABLED
    }

    protected static final Identifier BUTTON_SOUND = new Identifier("gui.button.press");
    private SoundEvent sound = SoundEvents.UI_BUTTON_CLICK.value();


    public GuiIconButton() {
        super();
    }

    public GuiIconButton(GuiContainer<?> container) {
        super(container);
    }

    protected abstract Identifier getTexture();
    protected abstract ReadableDimension getTextureSize();
    protected abstract ReadableDimension getSpriteSize();
    protected abstract ReadablePoint getSpriteUV(ButtonState state);

    protected ReadableDimension getButtonSize() {
        return getSpriteSize();
    }

    @Override
    public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
        super.draw(renderer, size, renderInfo);

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        Identifier texture = getTexture();
        ReadableDimension textureSize = getTextureSize();
        ReadableDimension spriteSize = getSpriteSize();
        ReadableDimension buttonSize = getButtonSize();
        ReadablePoint uv = getSpriteUV(getButtonState(renderInfo));

        renderer.bindTexture(texture);
        renderer.drawTexturedRect(0, 0, uv.getX(), uv.getY(), buttonSize.getWidth(), buttonSize.getHeight(),
                spriteSize.getWidth(), spriteSize.getHeight(), textureSize.getWidth(), textureSize.getHeight());
        
    }

    @Override
    protected ReadableDimension calcMinSize() {
        return getButtonSize();
    }

    protected ButtonState getButtonState(RenderInfo renderInfo) {
        if (!isEnabled()) {
            return ButtonState.DISABLED;
        } else if (isMouseHovering(new Point(renderInfo.getMouseX(), renderInfo.getMouseY()))) {
            return ButtonState.HOVER;
        } else {
            return ButtonState.DEFAULT;
        }
    }

    public final SoundEvent getSound() {
        return sound;
    }

    public void setSound(SoundEvent sound) {
        this.sound = sound;
    }

    @Override
    protected void onClick() {
        getMinecraft().getSoundManager().play(PositionedSoundInstance.master(sound, 1));
        super.onClick();
    }

}
