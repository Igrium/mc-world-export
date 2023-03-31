package org.scaffoldeditor.worldexport.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiTextField;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableColor;

/**
 * A text field that takes a number value;
 */
public abstract class GuiNumberField<N extends Number> extends AbstractGuiTextField<GuiNumberField<N>> {
    
    private N numberValue;
    private NumberFormatException lastException;

    private boolean suppressUpdate;

    @Override
    protected void onTextChanged(String from) {
        evaluate();
        super.onTextChanged(from);
    }

    /**
     * Re-evaluate the number in the text field.
     */
    public void evaluate() {
        try {
            numberValue = parse(getText());
            lastException = null;
            setTextColor(ReadableColor.WHITE);
        } catch (NumberFormatException e) {
            numberValue = null;
            lastException = e;
            setTextColor(ReadableColor.RED);
        }
    }

    @Override
    public GuiNumberField<N> setText(String text) {
        super.setText(text);
        if (!suppressUpdate) evaluate();
        return getThis();
    }
    

    /**
     * Parse the number from a string. If the string is empty, this method should
     * return <code>0</code>.
     * 
     * @param str The number string.
     * @return The parsed number.
     * @throws NumberFormatException If the string is not a valid number.
     */
    protected abstract N parse(String str) throws NumberFormatException;

    /**
     * Parse the number from another number, potentially of a different type.
     * @param number The number to parse.
     * @return The number of this type.
     */
    protected abstract N parseNumber(Number number);

    /**
     * Whether the string in the text field is a valid number.
     */
    public boolean isValid() {
        return lastException == null;
    }

    /**
     * Set the number value of this text field.
     * @param value The new value.
     */
    public void setValue(Number value) {
        try {
            suppressUpdate = true;
            numberValue = parseNumber(value);
            lastException = null;
            setText(numberValue.toString());
        } finally {
            suppressUpdate = false;
        }
    }

    /**
     * Get the number that's in the text field.
     * @return The number.
     * @throws NumberFormatException If the string is not a valid number.
     */
    public N getValue() throws NumberFormatException {
        if (lastException != null) {
            throw lastException;
        }
        return numberValue;
    }

    public int intValue() throws NumberFormatException {
        return getValue().intValue();
    }

    public double doubleValue() throws NumberFormatException {
        return getValue().doubleValue();
    }
    
    public float floatValue() throws NumberFormatException {
        return getValue().floatValue();
    }

    @Override
    protected GuiNumberField<N> getThis() {
        return this;
    }

    public static class GuiDoubleField extends GuiNumberField<Double> {

        @Override
        protected Double parse(String str) throws NumberFormatException {
            return Double.valueOf(str);
        }

        @Override
        protected Double parseNumber(Number number) {
            return number.doubleValue();
        }
        
    }

    public static class GuiFloatField extends GuiNumberField<Float> {

        @Override
        protected Float parse(String str) throws NumberFormatException {
            return Float.valueOf(str);
        }

        @Override
        protected Float parseNumber(Number number) {
            return number.floatValue();
        }
        
    }

    public static class GuiIntField extends GuiNumberField<Integer> {

        @Override
        protected Integer parse(String str) throws NumberFormatException {
            return Integer.valueOf(str);
        }

        @Override
        protected Integer parseNumber(Number number) {
            return number.intValue();
        }
        
    }
    
}
