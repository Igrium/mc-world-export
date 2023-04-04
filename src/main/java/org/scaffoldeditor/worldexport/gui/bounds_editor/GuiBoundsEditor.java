package org.scaffoldeditor.worldexport.gui.bounds_editor;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class GuiBoundsEditor extends AbstractGuiPopup<GuiBoundsEditor> {

    private final GuiBoundsOverview overview;
    private final GuiButton closeButton = new GuiButton().setLabel("Close").onClick(this::close);

    public GuiBoundsEditor(GuiContainer<?> container, World world, int width, int height, ChunkPos rootPos) {
        super(container);
        overview = new GuiBoundsOverview(world, new OverviewData(width, height, rootPos));
        popup.setLayout(new VerticalLayout().setSpacing(10))
                .addElements(new VerticalLayout.Data(0.5), overview, closeButton);
    }

    public GuiBoundsOverview getOverview() {
        return overview;
    }

    @Override
    protected GuiBoundsEditor getThis() {
        return this;
    }
    
    @Override
    protected void close() {
        super.close();
        overview.close();
    }

    @Override
    public void open() {
        super.open();
    }
}
