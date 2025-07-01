package com.igrium.worldexport.world;

import lombok.Getter;
import net.minecraft.world.HeightLimitView;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/**
 * A simplified representation of a chunk that holds a vertical column of sections
 * @param <T> Section instance type.
 */
public class SimpleSectionColumn<T> implements HeightLimitView {

    private final AtomicReferenceArray<T> sections;

    @Getter
    private final int numSections;

    @Getter
    private final int lowerY;

    public SimpleSectionColumn(int lowerY, int numSections) {
        this.numSections = numSections;
        this.lowerY = lowerY;

        sections = new AtomicReferenceArray<>(numSections);
    }

    public SimpleSectionColumn(int lowerY, T[] sections) {
        this.numSections = sections.length;
        this.lowerY = lowerY;

        this.sections = new AtomicReferenceArray<>(sections);
    }

    public @Nullable T getSection(int index) {
        return sections.get(index);
    }

    public T getOrCreate(int index, Supplier<T> factory) {
        return sections.updateAndGet(index, val -> val != null ? val : factory.get());
    }

    public T putSection(int index, @Nullable T newValue) {
        return sections.getAndSet(index, newValue);
    }

    public boolean putIfAbsent(int index, T newValue) {
        return sections.compareAndSet(index, null, newValue);
    }

    @Override
    public int getHeight() {
        return numSections * 16;
    }

    @Override
    public int countVerticalSections() {
        return numSections;
    }

    @Override
    public int getBottomY() {
        return lowerY * 16;
    }

    @Override
    public int getBottomSectionCoord() {
        return lowerY;
    }

    @Override
    public int getTopSectionCoord() {
        return lowerY + numSections;
    }
}
