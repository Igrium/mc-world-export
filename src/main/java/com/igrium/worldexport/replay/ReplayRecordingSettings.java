package com.igrium.worldexport.replay;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.Objects;

@Getter
@ToString
public class ReplayRecordingSettings {

    @Setter
    private boolean recordWorld;

    @Setter @NonNull
    private String name = "recording";

    @NonNull
    private ChunkSectionPos minSection = ChunkSectionPos.from(0,0,0);

    @NonNull
    private ChunkSectionPos maxSection = minSection;

    public void setMinSection(@NonNull ChunkSectionPos minSection) {
        this.minSection = Objects.requireNonNull(minSection);
        validateMinMax();
    }

    public void setMaxSection(@NonNull ChunkSectionPos maxSection) {
        this.maxSection = Objects.requireNonNull(maxSection);
        validateMinMax();
    }

    public void setBounds(@NonNull ChunkSectionPos minSection, @NonNull ChunkSectionPos maxSection) {
        this.minSection = Objects.requireNonNull(minSection);
        this.maxSection = Objects.requireNonNull(maxSection);
        validateMinMax();
    }

    public boolean isInBounds(BlockPos pos) {
        int sectX = ChunkSectionPos.getSectionCoord(pos.getX());
        int sectY = ChunkSectionPos.getSectionCoord(pos.getY());
        int sectZ = ChunkSectionPos.getSectionCoord(pos.getZ());

        return minSection.getX() <= sectX && sectX <= maxSection.getX()
                && minSection.getY() <= sectY && sectY <= maxSection.getY()
                && minSection.getZ() <= sectZ && sectZ <= maxSection.getZ();
    }

    private void validateMinMax() {
        int minX = Math.min(minSection.getX(), maxSection.getX());
        int minY = Math.min(minSection.getY(), maxSection.getY());
        int minZ = Math.min(minSection.getZ(), maxSection.getZ());

        int maxX = Math.max(minSection.getX(), maxSection.getX());
        int maxY = Math.max(minSection.getY(), maxSection.getY());
        int maxZ = Math.max(minSection.getZ(), maxSection.getZ());

        minSection = ChunkSectionPos.from(minX, minY, minZ);
        maxSection = ChunkSectionPos.from(maxX, maxY, maxZ);
    }
}
