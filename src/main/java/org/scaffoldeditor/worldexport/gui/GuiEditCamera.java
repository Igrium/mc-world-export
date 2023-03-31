package org.scaffoldeditor.worldexport.gui;

import java.util.function.Consumer;

import org.scaffoldeditor.worldexport.gui.GuiNumberField.GuiDoubleField;
import org.scaffoldeditor.worldexport.gui.GuiNumberField.GuiIntField;
import org.scaffoldeditor.worldexport.replaymod.camera_animations.AbstractCameraAnimation;

import com.replaymod.core.versions.MCVer.Keyboard;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTextField;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiColorPicker;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Typeable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.GridLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Colors;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;

public class GuiEditCamera extends AbstractGuiPopup<GuiEditCamera> implements Typeable {

    protected final AbstractCameraAnimation animation;
    protected Consumer<AbstractCameraAnimation> onSave = anim -> {};

    public GuiEditCamera onSave(Consumer<AbstractCameraAnimation> onSave) {
        this.onSave = onSave;
        return this;
    }

    public final GuiLabel title = new GuiLabel().setI18nText("worldexport.gui.editcamera.title").setColor(Colors.BLACK);

    /*
     * INPUTS
     */

    private static final Dimension FIELD_SIZE = new Dimension(180, 20);
    
    public final GuiNumberField<Integer> idField = new GuiIntField().setSize(FIELD_SIZE).setDisabled();
    public final GuiTextField nameField = new GuiTextField().setSize(FIELD_SIZE);
    public final GuiNumberField<Double> startTimeField = new GuiDoubleField().setSize(FIELD_SIZE);
    public final GuiColorPicker colorPicker = new GuiColorPicker().setSize(FIELD_SIZE);
    
    // private static final Formatting[] COLORS = Arrays.stream(Formatting.values())
    //         .filter(Formatting::isColor).toArray(Formatting[]::new);

    // public final GuiDropdownMenu<Formatting> colorDropdown = new GuiDropdownMenu<Formatting>()
    //         .setToString(s -> getColorTranslation(s.getName()))
    //         .setValues(COLORS);
    
    // private static String getColorTranslation(String colorName) {
    //     String key = "worldexport.gui.color."+colorName;
    //     if (I18n.hasTranslation(key)) {
    //         return I18n.translate(key);
    //     } else {
    //         return colorName;
    //     }
    // }

    private final GuiPanel inputPanel = new GuiPanel()
            .setLayout(new GridLayout().setCellsEqualSize(false).setColumns(2).setSpacingX(3).setSpacingY(5))
            .addElements(new GridLayout.Data(1, 0.5),
                    new GuiLabel().setI18nText("worldexport.gui.editcamera.id").setColor(Colors.BLACK), idField,
                    new GuiLabel().setI18nText("worldexport.gui.editcamera.name").setColor(Colors.BLACK), nameField,
                    new GuiLabel().setI18nText("worldexport.gui.editcamera.start_time").setColor(Colors.BLACK), startTimeField,
                    new GuiLabel().setI18nText("worldexport.gui.editcamera.color").setColor(Colors.BLACK), colorPicker);

    /*
     * BUTTONS
     */
    
    public final GuiButton saveButton = new GuiButton()
            .onClick(this::save).setSize(150, 20).setI18nLabel("replaymod.gui.save");

    public final GuiButton cancelButton = new GuiButton()
            .onClick(this::close).setSize(150, 20).setI18nLabel("replaymod.gui.cancel");

    public final GuiPanel buttons = new GuiPanel()
            .setLayout(new HorizontalLayout(HorizontalLayout.Alignment.CENTER).setSpacing(7))
            .addElements(new HorizontalLayout.Data(0.5), saveButton, cancelButton);
    
    {
        setBackgroundColor(Colors.DARK_TRANSPARENT);
        popup.setLayout(new VerticalLayout().setSpacing(10))
                .addElements(new VerticalLayout.Data(.5, false), title, inputPanel, buttons);
    }

    @Override
    protected GuiEditCamera getThis() {
        return this;
    }

    public GuiEditCamera(GuiContainer<?> container, AbstractCameraAnimation animation) {
        super(container);
        this.animation = animation;

        idField.setValue(animation.getId());
        nameField.setText(animation.getName());
        startTimeField.setValue(animation.getStartTime());
        colorPicker.setColor(animation.getColor());
        // colorDropdown.setSelected(animation.getColor());

        startTimeField.onTextChanged(str -> {
            saveButton.setEnabled(startTimeField.isValid());
        });
    }

    @Override
    public boolean typeKey(ReadablePoint mousePosition, int keyCode, char keyChar, boolean ctrlDown, boolean shiftDown) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelButton.onClick();
            return true;
        } else if (keyCode == Keyboard.KEY_RETURN) {
            saveButton.onClick();
            return true;
        }
        return false;
    }

    public void save() {
        animation.setName(nameField.getText());
        animation.setStartTime(startTimeField.doubleValue());
        animation.setColor(colorPicker.getColor());

        close();
        onSave.accept(animation);
    }
    
    // Extend access
    public void open() {
        super.open();
    }
}
