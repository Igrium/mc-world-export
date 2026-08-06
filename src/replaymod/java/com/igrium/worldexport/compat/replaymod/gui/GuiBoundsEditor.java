package com.igrium.worldexport.compat.replaymod.gui;

import com.igrium.worldexport.math.Box2i;
import com.igrium.worldexport.math.ChunkSectionBox;
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
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GuiBoundsEditor extends AbstractGuiPopup<GuiBoundsEditor> {

    public enum EditMode {
        WORLD, UPDATE, ENTITY
    }

    private static final int WORLD_COLOR = 0xDDFF00FF;
    private static final int UPDATE_COLOR = 0xDDFFFF00;
    private static final int ENTITY_COLOR = 0xDD00FFFF;

    @Getter
    private final GuiBoundsOverview overview;

    // Per-mode bounds, kept as separate fields so each mode's edits are independent.
    private final Box2i worldBox = new Box2i();
    private final Box2i updateBox = new Box2i();
    private final Box2i entityBox = new Box2i();

    private int worldLowerDepth;
    private int updateLowerDepth;
    private int entityLowerDepth;

    private int worldUpperLimit;
    private int updateUpperLimit;
    private int entityUpperLimit;

    private final int minSection;
    private final int maxSection;

    private final List<Runnable> closeListeners = new ArrayList<>();

    @Getter
    private EditMode editMode = EditMode.WORLD;

    // --- Mode buttons ---

    private final GuiSelectableButton worldButton = new GuiSelectableButton(() -> editMode == EditMode.WORLD);
    private final GuiSelectableButton updateButton = new GuiSelectableButton(() -> editMode == EditMode.UPDATE);
    private final GuiSelectableButton entityButton = new GuiSelectableButton(() -> editMode == EditMode.ENTITY);

    {
        worldButton.setI18nLabel("worldexport.gui.export.world_bounds");
        updateButton.setI18nLabel("worldexport.gui.export.update_bounds");
        entityButton.setI18nLabel("worldexport.gui.export.entity_bounds");

        worldButton.onClick(() -> setEditMode(EditMode.WORLD));
        updateButton.onClick(() -> setEditMode(EditMode.UPDATE));
        entityButton.onClick(() -> setEditMode(EditMode.ENTITY));
    }

    private final GuiPopupBackground background = new GuiPopupBackground();

    private final GuiPanel topPanel = new GuiPanel()
            .addElements(null, worldButton, updateButton, entityButton)
            .setLayout(new CustomLayout<GuiPanel>() {
                @Override
                protected void layout(GuiPanel guiPanel, int width, int height) {
                    int margin = 4;
                    int btnWidth = (width - margin * 4) / 3;

                    pos(worldButton, margin, 0);
                    width(worldButton, btnWidth);

                    pos(updateButton, margin * 2 + btnWidth, 0);
                    width(updateButton, btnWidth);

                    pos(entityButton, margin * 3 + btnWidth * 2, 0);
                    width(entityButton, btnWidth);
                }

                @Override
                public ReadableDimension calcMinSize(GuiContainer<?> container) {
                    return new Dimension(0, worldButton.getMinSize().getHeight());
                }
            });

    // --- Depth sliders ---

    public final GuiSlider upperLimitSlider = new GuiSlider().onValueChanged(this::handleChangeUpperLimit)
            .setHeight(20).setSteps(32);

    private void handleChangeUpperLimit() {
        setUpperLimit(editMode, getUpperLimitSlider());
    }

    private int getUpperLimitSlider() {
        return upperLimitSlider.getValue() + minSection;
    }

    private void setUpperLimitSlider(int upperLimit) {
        upperLimitSlider.setSteps(maxSection - minSection);
        // setValue always fires onValueChanged, so only touch it when it's actually out of date.
        if (getUpperLimitSlider() != upperLimit) {
            upperLimitSlider.setValue(upperLimit - minSection);
        }

        upperLimitSlider.setI18nText("worldexport.gui.export.upper_limit", upperLimit * 16 + 15);
        overview.setTopY(upperLimit * 16 + 15);
    }

    public final GuiSlider lowerDepthSlider = new GuiSlider().onValueChanged(this::handleChangeLowerDepth)
            .setHeight(20).setSteps(32);

    private void handleChangeLowerDepth() {
        setLowerDepth(editMode, getLowerDepthSlider());
    }

    private int getLowerDepthSlider() {
        return lowerDepthSlider.getValue() + minSection;
    }

    private void setLowerDepthSlider(int lowerDepth) {
        lowerDepthSlider.setSteps(maxSection - minSection);
        // setValue always fires onValueChanged, so only touch it when it's actually out of date.
        if (getLowerDepthSlider() != lowerDepth) {
            lowerDepthSlider.setValue(lowerDepth - minSection);
        }

        lowerDepthSlider.setI18nText("worldexport.gui.export.lower_depth", lowerDepth * 16);
        overview.setBottomY(lowerDepth * 16);
    }

    private final GuiButton closeButton =
            new GuiButton().setI18nLabel("worldexport.gui.export.apply").onClick(this::close);

    private final GuiPanel bottomPanel = new GuiPanel().setLayout(new VerticalLayout().setSpacing(5))
            .addElements(new VerticalLayout.Data(0.5), upperLimitSlider, lowerDepthSlider, closeButton);

    // --- Per-mode bounds accessors ---

    public void setUpperLimit(EditMode mode, int upperLimit) {
        upperLimit = Math.clamp(upperLimit, minSection, maxSection);
        switch (mode) {
            case WORLD -> worldUpperLimit = upperLimit;
            case UPDATE -> updateUpperLimit = upperLimit;
            case ENTITY -> entityUpperLimit = upperLimit;
        }
        // Stored above first, so this bounces back at most once.
        if (getLowerDepth(mode) > upperLimit) {
            setLowerDepth(mode, upperLimit);
        }
        if (mode == editMode) {
            setUpperLimitSlider(upperLimit);
        }
    }

    public int getUpperLimit(EditMode mode) {
        return switch (mode) {
            case WORLD -> worldUpperLimit;
            case UPDATE -> updateUpperLimit;
            case ENTITY -> entityUpperLimit;
        };
    }

    public void setLowerDepth(EditMode mode, int lowerDepth) {
        lowerDepth = Math.clamp(lowerDepth, minSection, maxSection);
        switch (mode) {
            case WORLD -> worldLowerDepth = lowerDepth;
            case UPDATE -> updateLowerDepth = lowerDepth;
            case ENTITY -> entityLowerDepth = lowerDepth;
        }
        // Stored above first, so this bounces back at most once.
        if (getUpperLimit(mode) < lowerDepth) {
            setUpperLimit(mode, lowerDepth);
        }
        if (mode == editMode) {
            setLowerDepthSlider(lowerDepth);
        }
    }

    public int getLowerDepth(EditMode mode) {
        return switch (mode) {
            case WORLD -> worldLowerDepth;
            case UPDATE -> updateLowerDepth;
            case ENTITY -> entityLowerDepth;
        };
    }

    public Box2i getBox(EditMode mode) {
        return switch (mode) {
            case WORLD -> worldBox;
            case UPDATE -> updateBox;
            case ENTITY -> entityBox;
        };
    }

    private static int colorOf(EditMode mode) {
        return switch (mode) {
            case WORLD -> WORLD_COLOR;
            case UPDATE -> UPDATE_COLOR;
            case ENTITY -> ENTITY_COLOR;
        };
    }

    public void setEditMode(EditMode editMode) {
        this.editMode = editMode;

        // The active mode is shown at full opacity in slot 0; the others follow, dimmed.
        int index = 0;
        overview.setBounds(index, getBox(editMode));
        overview.setColor(index, colorOf(editMode));
        index++;

        for (EditMode other : EditMode.values()) {
            if (other == editMode) continue;
            overview.setBounds(index, getBox(other));
            overview.setColor(index, ARGB.multiplyAlpha(colorOf(other), .4f));
            index++;
        }

        setLowerDepthSlider(getLowerDepth(editMode));
        setUpperLimitSlider(getUpperLimit(editMode));
    }

    public GuiBoundsEditor(GuiContainer<?> container, Level world, int width, int height, ChunkPos rootPos) {
        super(container);
        // jGui's own popup background is really inefficient; see GuiPopupBackground.
        disablePopupBackground();
        minSection = world.getMinSectionY();
        maxSection = world.getMaxSectionY();

        var overviewData = new OverviewData(width, height, rootPos);
        overview = new GuiBoundsOverview(world, overviewData);

        // Default bounds
        int centerX = overviewData.getOrigin().x() + overviewData.getWidth() / 2;
        int centerZ = overviewData.getOrigin().z() + overviewData.getHeight() / 2;

        worldBox.set(centerX - 4, centerZ - 4, centerX + 4, centerZ + 4);
        updateBox.set(worldBox);
        entityBox.set(worldBox);

        worldLowerDepth = minSection;
        updateLowerDepth = minSection;
        entityLowerDepth = minSection;

        worldUpperLimit = maxSection;
        updateUpperLimit = maxSection;
        entityUpperLimit = maxSection;

        GuiPanel content = new GuiPanel().setLayout(new CustomLayout<GuiPanel>() {

            @Override
            protected void layout(GuiPanel panel, int width, int height) {
                pos(topPanel, 0, 0);
                width(topPanel, width);

                pos(bottomPanel, 0, height - height(bottomPanel));
                width(bottomPanel, width);

                pos(overview, 0, height(topPanel));
                width(overview, width);
                height(overview, height - height(bottomPanel) - height(topPanel) - 5);
            }

        }).addElements(null, topPanel, overview, bottomPanel);

        // Same shape as jGui's own popup: the frame fills this panel and the content is inset from it.
        popup.setLayout(new CustomLayout<GuiPanel>() {

            @Override
            protected void layout(GuiPanel panel, int width, int height) {
                pos(background, 0, 0);
                size(background, width, height);

                pos(content, 10, 10);
                size(content, width - 20, height - 20);
            }

            @Override
            public ReadableDimension calcMinSize(GuiContainer<?> localContainer) {
                ReadableDimension screenSize = getContainer().getMinSize();
                return new Dimension(Math.clamp(screenSize.getWidth() - 64, 128, 384),
                        Math.clamp(screenSize.getHeight() - 64, 128, 384));
            }

        }).addElements(null, background, content);

        setEditMode(editMode);
    }

    public ChunkSectionBox getBounds(EditMode mode) {
        Box2i bounds = getBox(mode);
        return ChunkSectionBox.from(bounds.getX1(), getLowerDepth(mode), bounds.getY1(),
                bounds.getX2(), getUpperLimit(mode), bounds.getY2());
    }

    public void setBounds(EditMode mode, ChunkSectionBox bounds) {
        getBox(mode).set(bounds.minX(), bounds.minZ(), bounds.maxXInclusive(), bounds.maxZInclusive());
        setLowerDepth(mode, bounds.minY());
        setUpperLimit(mode, bounds.maxYInclusive());
    }

    public void setBounds(ChunkSectionBox bounds) {
        setBounds(editMode, bounds);
    }

    public void setBounds(EnumMap<EditMode, ChunkSectionBox> bounds) {
        for (var entry : bounds.entrySet()) {
            setBounds(entry.getKey(), entry.getValue());
        }
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

    public static CompletableFuture<EnumMap<EditMode, ChunkSectionBox>> openEditor(
            EnumMap<EditMode, ChunkSectionBox> defaultBounds, GuiContainer<?> container,
            Level world, int width, int height, ChunkPos rootPos) {
        GuiBoundsEditor editor = new GuiBoundsEditor(container, world, width, height, rootPos);
        editor.setBounds(defaultBounds);

        CompletableFuture<EnumMap<EditMode, ChunkSectionBox>> future = new CompletableFuture<>();
        editor.onClose(() -> {
            EnumMap<EditMode, ChunkSectionBox> map = new EnumMap<>(EditMode.class);
            for (var mode : EditMode.values()) {
                map.put(mode, editor.getBounds(mode));
            }
            future.complete(map);
        });

        editor.open();
        return future;
    }
}