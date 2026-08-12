package com.igrium.worldexport.compat.replaymod.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import lombok.Getter;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class GuiToggleBoolButton extends AbstractGuiButton<GuiToggleBoolButton> {

    private static final Color OVERLAY_COLOR = new Color(0, 0, 0, 80);

    @Getter
    private final MutableBoolean checked;

    public GuiToggleBoolButton(MutableBoolean checked) {
        this.checked = checked;
    }

    public GuiToggleBoolButton() {
        this.checked = new MutableBoolean();
    }

    public boolean isChecked() {
        return checked.get();
    }

    public void setChecked(boolean checked) {
        this.checked.setValue(checked);
    }

    @Override
    public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
        super.draw(renderer, size, renderInfo);
        // Semi-transparent overlay makes it looked "pressed in"
        if (isChecked()) {
            renderer.drawRect(0, 0, size.getWidth(), size.getHeight(), OVERLAY_COLOR);
        }
    }

    @Override
    public void onClick(Click click) {
        setChecked(!isChecked());
        super.onClick(click);
    }

    @Override
    protected GuiToggleBoolButton getThis() {
        return this;
    }
}
