package org.scaffoldeditor.worldexport.gui;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.scaffoldeditor.worldexport.replaymod.AnimatedCameraEntity;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.AbstractCameraAnimation;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.CameraAnimationModule;

import com.replaymod.core.ReplayMod;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiClickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiVerticalList;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Closeable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.GuiFileChooserPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.replay.ReplayHandler;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

public class GuiCameraManager extends GuiScreen implements Closeable {
    public static final Identifier TRASH_ICON = new Identifier("worldexport", "icons/trash.png");

    protected static final int ENTRY_WIDTH = 200;

    public final GuiPanel contentPanel = new GuiPanel(this).setBackgroundColor(Colors.DARK_TRANSPARENT);
    public final GuiLabel camerasLabel = new GuiLabel(contentPanel)
            .setI18nText("worldexport.gui.allcameras");
    public final GuiVerticalList camerasScrollable = new GuiVerticalList(contentPanel)
            .setDrawSlider(true).setDrawShadow(true);
    public final GuiButton importButton = new GuiButton(contentPanel)
            .setI18nLabel("worldexport.input.importcamera").onClick(this::openFileChooser);

    {
        setBackground(Background.NONE);
        setTitle(new GuiLabel().setI18nText("worldexport.gui.animatedcameras"));
        setLayout(new CustomLayout<GuiScreen>() {
            @Override
            protected void layout(GuiScreen container, int width, int height) {
                size(contentPanel, ENTRY_WIDTH + 30, height - 40);
                pos(contentPanel, width / 2 - width(contentPanel) / 2, 20);
            }
        });
        contentPanel.setLayout(new CustomLayout<GuiPanel>() {
            @Override
            protected void layout(GuiPanel container, int width, int height) {
                pos(importButton, 10, height - 10 - height(importButton));
                size(importButton, width - 10 - 5, height(importButton));

                pos(camerasLabel, 10, 10);
                pos(camerasScrollable, 10, y(camerasLabel) + height(camerasLabel) + 5);
                size(camerasScrollable, width - 10 - 5, height - 15 - height(importButton) - y(camerasScrollable));
            }
        });
    }

    protected final CameraAnimationModule module;
    protected final ReplayHandler handler;

    public GuiCameraManager(CameraAnimationModule module, ReplayHandler handler) {
        this.module = module;
        this.handler = handler;

        refreshList();
        handler.getOverlay().setVisible(false);
    }
    
    @SuppressWarnings("rawtypes")
    protected void refreshList() {
        Map<Integer, AbstractCameraAnimation> animations = module.getAnimations(handler.getReplayFile());
        List<Integer> ids = animations.keySet().stream().sorted().toList();

        // For some reason there isn't a dedicated clear function.
        GuiPanel listPanel = camerasScrollable.getListPanel();
        Collection<GuiElement> elements = Set.copyOf(listPanel.getElements().keySet());
        elements.forEach(listPanel::removeElement);
        
        for (int id : ids) {
            AbstractCameraAnimation animation = animations.get(id);
            GuiClickable panel = new GuiClickable().setLayout(new HorizontalLayout().setSpacing(5)).addElements(
                    new HorizontalLayout.Data(0.5),
                    new GuiLabel().setText(String.valueOf(id)),
                    new GuiLabel().setText(animation.getName()).setColor(animation.getColor())
            ).onClick(() -> {
                AnimatedCameraEntity ent = module.getCameraEntity(
                        (ClientWorld) handler.getCameraEntity().getWorld(), id);
                handler.spectateEntity(ent);
            });
            // GuiButton delete = new GuiButton().setTexture(TRASH_ICON).onClick(() -> delete(animation)).setSize(16, 16);
            GuiIconButton<?> delete = GuiIconButtons.TRASH.create().onClick(() -> delete(animation));
            GuiIconButton<?> edit = GuiIconButtons.PENCIL.create().onClick(() -> edit(animation));

            new GuiPanel(camerasScrollable.getListPanel()).setLayout(new CustomLayout<GuiPanel>() {
                @Override
                protected void layout(GuiPanel container, int width, int height) {
                    pos(panel, 5, 0);
                    // size(delete, 8, 8);
                    pos(delete, width - width(delete) - 5, height / 2 - height(delete) / 2);
                    pos(edit, width - width(delete) - width(edit) - 10, height / 2 - height(edit) / 2);
                }

                @Override
                public ReadableDimension calcMinSize(GuiContainer<?> container) {
                    return new Dimension(ENTRY_WIDTH, delete.getMinSize().getHeight());
                }
            }).addElements(null, panel, edit, delete);
        }
    }
    
    public void delete(AbstractCameraAnimation animation) {
        GuiConfirmPopup.open(this,
                "Are you sure you want to delete '" + animation.getName() + "'?")
                .setConfirmLabel("Delete")
                .onConfirmed(() -> deleteImpl(animation));
        
    }

    public void edit(AbstractCameraAnimation animation) {
        new GuiEditCamera(this, animation).onSave(anim -> {
            module.addAnimationsAsync(handler.getReplayFile(), Map.of(anim.getId(), anim)).exceptionally((e) -> {
                LogManager.getLogger().error("Error saving camera animation: ", e);
                ReplayMod.instance.printWarningToChat("worldexport.chat.camerasavefailed");
                return null;
            });
            refreshList();
        }).open();
    }

    protected void deleteImpl(AbstractCameraAnimation animation) {
        try {
            module.removeAnimation(handler.getReplayFile(), animation);
        } catch (IOException e) {
            LogManager.getLogger().error("Error removing animation from file.", e);
        }
        refreshList();
    }

    /**
     * Called when the import button is clicked.
     */
    public void openFileChooser() {
        GuiFileChooserPopup chooser = GuiFileChooserPopup.openLoadGui(this, "Import Camera", "xml");
        chooser.onAccept(file -> {
            try {
                module.importAnimation(handler.getReplayFile(), file);
                refreshList();
            } catch (IOException e) {
                LogManager.getLogger().error("Error importing camera animation: ", e);
                ReplayMod.instance.printWarningToChat("worldexport.chat.camerafailed");
            }
        });
    }

    @Override
    public void close() {
        handler.getOverlay().setVisible(true);
    }
    
}
