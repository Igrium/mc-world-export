package com.igrium.worldexport.compat.replaymod.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiVerticalList;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import lombok.Setter;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.Collection;

public class GuiCompatWarning extends AbstractGuiScreen<GuiCompatWarning> {
    private final GuiVerticalList content = new GuiVerticalList(this).setDrawShadow(true).setDrawSlider(true);

    private final GuiButton contButton = new GuiButton().setI18nLabel("worldexport.gui.continue")
            .setSize(200, 20)
            .onClick(this::cont);
    private final GuiButton cancelButton = new GuiButton().setI18nLabel("gui.cancel")
            .setSize(200, 20)
            .onClick(this::cancel);

    private final GuiPanel closeButtons = new GuiPanel(this).setLayout(new HorizontalLayout().setSpacing(5))
            .addElements(null, contButton, cancelButton);

    @Setter
    private Runnable contCallback = null;

    @Setter
    private Runnable cancelCallback = null;

    {
        setTitle(new GuiLabel().setI18nText("worldexport.gui.compatwarning.title"));
        setLayout(new CustomLayout<GuiCompatWarning>() {
            @Override
            protected void layout(GuiCompatWarning guiCompatWarning, int width, int height) {
                pos(content, 10, 35);
                pos(closeButtons, width / 2 - width(closeButtons) / 2, height - 10 - height(closeButtons));
                size(content, width - 20, y(closeButtons) - 10 - y(content));
            }
        });

        content.getListLayout().setSpacing(8);
    }

    public GuiCompatWarning(Collection<ModMetadata> breaks) {
        VerticalLayout.Data data = new VerticalLayout.Data(0.5);
        GuiPanel content = this.content.getListPanel();
        content.addElements(data, new GuiLabel().setI18nText("worldexport.gui.compatwarning.desc"));

        for (var mod : breaks) {
            content.addElements(data, new GuiLabel().setText(mod.getName() + " (" + mod.getId() + ")"));
        }
    }

    @Override
    protected GuiCompatWarning getThis() {
        return this;
    }

    private void cont() {
        if (contCallback != null) contCallback.run();
    }

    private void cancel() {
        if (cancelCallback != null) cancelCallback.run();
    }
}