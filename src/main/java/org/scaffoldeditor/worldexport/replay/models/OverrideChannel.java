package org.scaffoldeditor.worldexport.replay.models;

import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Represents a material override in the context of a replay entity;
 */
public class OverrideChannel {
    public enum Mode { VECTOR, SCALAR }
    
    private Mode mode;
    private String name;

    public OverrideChannel(String name, Mode mode) {
        this.name = name;
        this.mode = mode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Mode getMode() {
        return mode;
    }

    public Element serialize(Document doc) {
        Element element = doc.createElement("override_channel");
        element.setAttribute("name", name);
        element.setAttribute("type", mode == Mode.VECTOR ? "vector" : "scalar");

        return element;
    }
    
    public static OverrideChannel parse(Element element) {
        String name = element.getAttribute("name");
        String mode = element.getAttribute("type");
        if (!(mode.equals("vector") || mode.equals("scalar"))) {
            throw new IllegalArgumentException("type must be one of 'vector' or 'scalar'.");
        }

        return new OverrideChannel(name, mode.equals("vector") ? Mode.VECTOR : Mode.SCALAR);
    }

    public static class OverrideChannelFrame {
        private Mode mode;
        private Vector3fc vector;
        private float scalar;

        public OverrideChannelFrame(Vector3fc vector) {
            this.vector = vector;
            this.mode = Mode.VECTOR;
        }
        
        public OverrideChannelFrame(Vector3dc vector) {
            this.vector = new Vector3f((float) vector.x(), (float) vector.y(), (float) vector.z());
            this.mode = Mode.VECTOR;
        }

        public OverrideChannelFrame(float scalar) {
            this.scalar = scalar;
            this.mode = Mode.SCALAR;
        }

        public Mode getMode() {
            return mode;
        }

        public Vector3fc getVector() {
            if (mode != Mode.VECTOR) {
                throw new IllegalStateException("This frame does not contain a vector value.");
            }
            return vector;
        }

        public float getScalar() {
            if (mode != Mode.SCALAR) {
                throw new IllegalStateException("This frame does not contain a scalar value.");
            }
            return scalar;
        }

        @Override
        public String toString() {
            if (this.mode == Mode.VECTOR) {
                return vector.x()+" "+vector.y()+" "+vector.z();
            } else {
                return String.valueOf(scalar);
            }
        }
    }
}
