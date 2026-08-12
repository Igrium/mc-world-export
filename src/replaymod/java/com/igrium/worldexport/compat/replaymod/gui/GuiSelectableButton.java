package com.igrium.worldexport.compat.replaymod.gui;

import com.igrium.worldexport.compat.replaymod.util.LabelColorProvider;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiButton;

import java.util.function.BooleanSupplier;

public class GuiSelectableButton extends AbstractGuiButton<GuiSelectableButton> implements LabelColorProvider {

    private final BooleanSupplier isSelected;

    public GuiSelectableButton(BooleanSupplier isSelected) {
        this.isSelected = isSelected;
    }


    @Override
    public int getLabelColor(int base) {
        return isSelected.getAsBoolean() ? 0xf7b345 : base;
    }

    @Override
    protected GuiSelectableButton getThis() {
        return this;
    }
}
