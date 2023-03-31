package org.scaffoldeditor.worldexport.gui;

import com.replaymod.core.versions.MCVer.Keyboard;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Typeable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;

public class GuiConfirmPopup extends AbstractGuiPopup<GuiConfirmPopup> implements Typeable {

    public static GuiConfirmPopup open(GuiContainer<?> container, String... info) {
        GuiElement<?>[] labels = new GuiElement[info.length];
        for (int i = 0; i < info.length; i++) {
            labels[i] = new GuiLabel().setI18nText(info[i]).setColor(Colors.BLACK);
        }
        return open(container, labels);
    }

    public static GuiConfirmPopup open(GuiContainer<?> container, GuiElement<?>... info) {
        GuiConfirmPopup popup = new GuiConfirmPopup(container).setBackgroundColor(Colors.DARK_TRANSPARENT);
        popup.getInfo().addElements(new VerticalLayout.Data(0.5), info);
        popup.open();
        return popup;
    }

    private Runnable onCanceled = () -> {};
    private Runnable onConfirmed = () -> {};

    final int BUFFER = 5;

    private final GuiButton cancelButton = new GuiButton().setSize(150, 20).onClick(() -> {
        close();
        onCanceled.run();
    }).setLabel("Cancel");

    private final GuiButton confirmButton = new GuiButton().setSize(150, 20).onClick(() -> {
        close();
        onConfirmed.run();
    }).setLabel("OK");

    private final GuiPanel buttonPanel = new GuiPanel().setLayout(new CustomLayout<GuiPanel>() {

        @Override
        protected void layout(GuiPanel panel, int width, int height) {
            final int BUFFER = 5;
            size(cancelButton, width / 2 - BUFFER * 2, 20);
            size(confirmButton, width / 2 - BUFFER * 2, 20);

            pos(confirmButton, BUFFER, 0);
            pos(cancelButton, width - BUFFER - width(cancelButton), 0);
        }
        
    }).addElements(null, confirmButton, cancelButton).setSize(150, 20);

    private final GuiPanel info = new GuiPanel().setMinSize(new Dimension(320, 50))
            .setLayout(new VerticalLayout(VerticalLayout.Alignment.TOP).setSpacing(2));
    
    {
        popup.setLayout(new VerticalLayout().setSpacing(10))
                .addElements(new VerticalLayout.Data(0.5), info, buttonPanel);
    }

    private int layer;

    public GuiConfirmPopup(GuiContainer<?> container) {
        super(container);
    }

    public GuiConfirmPopup setCancelLabel(String label) {
        cancelButton.setLabel(label);
        return this;
    }

    public GuiConfirmPopup setCancelI18nLabel(String label, Object... args) {
        cancelButton.setI18nLabel(label, args);
        return this;
    }

    public GuiConfirmPopup setConfirmLabel(String label) {
        confirmButton.setLabel(label);
        return this;
    }

    public GuiConfirmPopup setConfirmI18nLabel(String label, Object... args) {
        confirmButton.setI18nLabel(label, args);
        return this;
    }

    public GuiConfirmPopup onCanceled(Runnable onCanceled) {
        this.onCanceled = onCanceled;
        return this;
    }

    public GuiConfirmPopup onConfirmed(Runnable onConfirmed) {
        this.onConfirmed = onConfirmed;
        return this;
    }

    @Override
    public boolean typeKey(ReadablePoint mousePosition, int keyCode, char keyChar, boolean ctrlDown, boolean shiftDown) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelButton.onClick();
            return true;
        } else if (keyCode == Keyboard.KEY_RETURN) {
            confirmButton.onClick();
            return true;
        }
        return false;
    }

    @Override
    protected GuiConfirmPopup getThis() {
        return this;
    }

    public GuiButton getCancelButton() {
        return cancelButton;
    }

    public GuiButton getConfirmButton() {
        return confirmButton;
    }

    public GuiPanel getInfo() {
        return info;
    }

    public int getLayer() {
        return layer;
    }

    public GuiConfirmPopup setLayer(int layer) {
        this.layer = layer;
        return this;
    }
    
}
