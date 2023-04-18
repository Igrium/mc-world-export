package org.scaffoldeditor.worldexport.gui.bounds_editor;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.scaffoldeditor.worldexport.util.Box2i;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiSlider;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class GuiBoundsEditor extends AbstractGuiPopup<GuiBoundsEditor> {

    private final GuiBoundsOverview overview;

    private int minSection;
    private int maxSection = 16;

    private List<Runnable> closeListeners = new LinkedList<>();

    public final GuiSlider upperLimitSlider = new GuiSlider().onValueChanged(this::handleChangeUpperLimit)
            .setHeight(20).setSteps(32);

    private void handleChangeUpperLimit() {
        upperLimitSlider.setI18nText("worldexport.gui.export.upper_limit", getUpperLimit() * 16 + 15);
        overview.setTopY(getUpperLimit() * 16 + 15);

        if (getUpperLimit() < getLowerDepth()) {
            setLowerDepth(getUpperLimit());
        }
    }

    public void setUpperLimit(int upperLimit) {
        upperLimitSlider.setSteps(maxSection - minSection);
        upperLimitSlider.setValue(upperLimit - minSection);
    }

    public int getUpperLimit() {
        return upperLimitSlider.getValue() + minSection;
    }

    public final GuiSlider lowerDepthSlider = new GuiSlider().onValueChanged(this::handleChangeLowerDepth)
            .setHeight(20).setSteps(32);

    private void handleChangeLowerDepth() {
        lowerDepthSlider.setI18nText("worldexport.gui.export.lower_depth", getLowerDepth() * 16);
        overview.setBottomY(getLowerDepth() * 16);

        if (getLowerDepth() > getUpperLimit()) {
            setUpperLimit(getLowerDepth());
        }
    }

    public void setLowerDepth(int lowerDepth) {
        lowerDepthSlider.setSteps(maxSection - minSection);
        lowerDepthSlider.setValue(lowerDepth - minSection);
    }

    public int getLowerDepth() {
        return lowerDepthSlider.getValue() + minSection;
    }

    private final GuiButton closeButton = new GuiButton().setI18nLabel("worldexport.gui.export.apply").onClick(this::close);

    private final GuiPanel bottomPanel = new GuiPanel().setLayout(new VerticalLayout().setSpacing(5))
            .addElements(new VerticalLayout.Data(0.5), upperLimitSlider, lowerDepthSlider, closeButton);

    public GuiBoundsEditor(GuiContainer<?> container, World world, int width, int height, ChunkPos rootPos) {
        super(container);

        minSection = world.getBottomSectionCoord();
        maxSection = world.getTopSectionCoord();

        overview = new GuiBoundsOverview(world, new OverviewData(width, height, rootPos));
        popup.setLayout(new CustomLayout<GuiPanel>() {

            @Override
            protected void layout(GuiPanel panel, int width, int height) {
                pos(bottomPanel, 0, height - height(bottomPanel));
                width(bottomPanel, width);

                size(overview, container.getMinSize());

                pos(overview, 0, 0);
                width(overview, width);
                height(overview, height - height(bottomPanel) - 5);
                
            }

            @Override
            public ReadableDimension calcMinSize(GuiContainer<?> localContainer) {
                // ReadableDimension overviewSize = overview.calcMinSize();
                // ReadableDimension panelSize = bottomPanel.calcMinSize();

                // return new Dimension(Math.max(overviewSize.getWidth(), panelSize.getWidth()),
                //         overviewSize.getHeight() + panelSize.getHeight() + 5);

                ReadableDimension containerMin = container.getMinSize();
                return new Dimension(Math.min(384, containerMin.getWidth() - 64),
                        Math.min(384, containerMin.getHeight() - 64));

                // return new Dimension(containerMin.getWidth() - 128, containerMin.getHeight() - 128);
            }
            
        }).addElements(null, overview, bottomPanel);
        
        

        // overview.setSize(256, 266);
        // popup.setLayout(new VerticalLayout().setSpacing(10))
        //         .addElements(new VerticalLayout.Data(0.5), overview, upperLimitSlider, lowerDepthSlider, closeButton);

        setLowerDepth(minSection);
        setUpperLimit(maxSection);
    }

    public GuiBoundsOverview getOverview() {
        return overview;
    }

    @Override
    protected GuiBoundsEditor getThis() {
        return this;
    }

    public BlockBox getBounds() {
        Box2i bounds = overview.getBounds();
        return new BlockBox(bounds.getX1(), getLowerDepth(), bounds.getY1(),
                bounds.getX2(), getUpperLimit(), bounds.getY2());
    }

    public void setBounds(BlockBox bounds) {
        Box2i bounds2d = new Box2i(bounds.getMinX(), bounds.getMinZ(), bounds.getMaxX(), bounds.getMaxZ());

        overview.setBounds(bounds2d);
        setLowerDepth(bounds.getMinY());
        setUpperLimit(bounds.getMaxY());
    }
    
    @Override
    protected void close() {
        super.close();
        overview.close();
        closeListeners.forEach(Runnable::run);
        closeListeners.clear();
    }

    @Override
    public void open() {
        super.open();
    }

    public void onClose(Runnable r) {
        closeListeners.add(r);
    }

    public static CompletableFuture<BlockBox> openEditor(BlockBox defaultBounds, GuiContainer<?> container, World world, int width, int height, ChunkPos rootPos) {
        GuiBoundsEditor editor = new GuiBoundsEditor(container, world, width, height, rootPos);
        editor.setBounds(defaultBounds);

        CompletableFuture<BlockBox> future = new CompletableFuture<>();
        editor.onClose(() -> {
            future.complete(editor.getBounds());
        });

        editor.open();
        return future;
    }
}
