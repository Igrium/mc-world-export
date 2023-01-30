package org.scaffoldeditor.worldexport.replaymod.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Tickable;

public class GuiReplayExporter extends GuiScreen implements Tickable {

    public final GuiLabel placeholderLabel = new GuiLabel(this).setText("It's exporting. Trust me.");

    @Override
    public void tick() {
        
    }
    
}
