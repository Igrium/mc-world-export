package com.igrium.worldexport.compat.replaymod.gui;

import com.igrium.worldexport.math.Box2i;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiSlider;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import lombok.Getter;
import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GuiBoundsEditor extends AbstractGuiPopup<GuiBoundsEditor> {

    @Getter
    private final GuiBoundsOverview overview;

    private int minSection;
    private int maxSection = 16;

    private final List<Runnable> closeListeners = new ArrayList<>();

    public final GuiSlider upperLimitSlider = new GuiSlider().onValueChanged(this::handleChangeUpperLimit)
            .setHeight(20).setSteps(32);

    private void handleChangeUpperLimit() {
        upperLimitSlider.setI18nText("worldexport.gui.export.upper_limit", getUpperLimit() * 16 + 15);
        overview.setTopY(getUpperLimit() * 16 + 15);

        if (getUpperLimit() < getLowerDepth()) {
            setLowerDepth(getUpperLimit());
        }
    }

    public int getUpperLimit() {
        return upperLimitSlider.getValue() + minSection;
    }

    public void setUpperLimit(int upperLimit) {
        upperLimitSlider.setSteps(maxSection - minSection);
        upperLimitSlider.setValue(upperLimit - minSection);
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

    public int getLowerDepth() {
        return lowerDepthSlider.getValue() + minSection;
    }

    public void setLowerDepth(int lowerDepth) {
        lowerDepthSlider.setSteps(maxSection - minSection);
        lowerDepthSlider.setValue(lowerDepth - minSection);
    }


    private final GuiButton closeButton = new GuiButton().setI18nLabel("worldexport.gui.export.apply").onClick(this::close);

    private final GuiPanel bottomPanel = new GuiPanel().setLayout(new VerticalLayout().setSpacing(5))
            .addElements(new VerticalLayout.Data(0.5), upperLimitSlider, lowerDepthSlider, closeButton);

    public GuiBoundsEditor(GuiContainer<?> container, Level world, int width, int height, ChunkPos rootPos) {
        super(container);

        minSection = world.getMinSectionY();
        maxSection = world.getMaxSectionY();

        overview = new GuiBoundsOverview(world, new OverviewData(width, height, rootPos));

        popup.setLayout(new CustomLayout<GuiPanel>() {

            @Override
            protected void layout(GuiPanel panel, int width, int height) {
                pos(bottomPanel, 0, height - height(bottomPanel));
                width(bottomPanel, width);

                pos(overview, 0, 0);
                width(overview, width);
                height(overview, height - height(bottomPanel) - 5);

            }

            @Override
            public ReadableDimension calcMinSize(GuiContainer<?> localContainer) {
                // The screen we've been opened on, resolved at layout time. The container passed to the
                // constructor is the parent popup, whose min size is always (0, 0).
                ReadableDimension screenSize = getContainer().getMinSize();
                return new Dimension(Math.clamp(screenSize.getWidth() - 64, 128, 384),
                        Math.clamp(screenSize.getHeight() - 64, 128, 384));
            }

        }).addElements(null, overview, bottomPanel);

        setLowerDepth(minSection);
        setUpperLimit(maxSection);
    }

    public BlockBox getBounds() {
        Box2i bounds = overview.getBounds();
        return BlockBox.of(new BlockPos(bounds.getX1(), getLowerDepth(), bounds.getY1()),
                new BlockPos(bounds.getX2(), getUpperLimit(), bounds.getY2()));

    }

    public void setBounds(BlockBox bounds) {
        Box2i bounds2d = new Box2i(bounds.min().getX(), bounds.min().getZ(), bounds.max().getX(), bounds.max().getZ());

        overview.setBounds(bounds2d);
        setLowerDepth(bounds.min().getY());
        setUpperLimit(bounds.max().getY());
    }

    @Override
    protected void close() {
        super.close();
        overview.close();
        closeListeners.forEach(Runnable::run);
        closeListeners.clear();
    }

    @Override
    protected GuiBoundsEditor getThis() {
        return this;
    }

    public void onClose(Runnable r) {
        closeListeners.add(r);
    }

    public static CompletableFuture<BlockBox> openEditor(BlockBox defaultBounds, GuiContainer<?> container, Level world, int width, int height, ChunkPos rootPos) {
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
