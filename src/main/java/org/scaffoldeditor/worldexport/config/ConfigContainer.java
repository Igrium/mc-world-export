package org.scaffoldeditor.worldexport.config;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * A class that can hold a dynamic selection of configuration elements.
 */
public interface ConfigContainer {
    public static class ConfigEntry<T> {
        private final String label;
        private final Class<T> type;
        private final Supplier<T> getter;
        private final Consumer<T> setter;
        private final Optional<String> tooltip;

        public ConfigEntry(String label, Class<T> type, Supplier<T> getter, Consumer<T> setter, @Nullable String tooltip) {
            this.label = label;
            this.type = type;
            this.getter = getter;
            this.setter = setter;
            this.tooltip = Optional.ofNullable(tooltip);
        }

        public ConfigEntry(String label, Class<T> type, Supplier<T> getter, Consumer<T> setter) {
            this(label, type, getter, setter, null);
        }
        
        public String getLabel() {
            return label;
        }

        public Class<T> getType() {
            return type;
        }

        public T get() {
            return getter.get();
        }

        public void set(T value) {
            setter.accept(value);
        }

        public void setValue(Object value) throws ClassCastException {
            set(getType().cast(value));
        }

        public Optional<String> getTooltip() {
            return tooltip;
        }
    }

    /**
     * Get all the entries in this config file.
     * @return The entries.
     */
    public Collection<ConfigEntry<?>> entries();
}
