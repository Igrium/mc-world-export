package com.igrium.worldexport.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;

public class JsonAdapters {
    static abstract class AbstractPositionAdapter<T extends Position> extends TypeAdapter<T> {
        @Override
        public void write(JsonWriter out, T value) throws IOException {
            out.beginArray();
            out.value(value.x());
            out.value(value.y());
            out.value(value.z());
            out.endArray();
        }

        @Override
        public T read(JsonReader in) throws IOException {
            in.beginArray();
            double x = in.nextDouble();
            double y = in.nextDouble();
            double z = in.nextDouble();
            in.endArray();
            return create(x, y, z);
        }

        abstract T create(double x, double y, double z);
    }

    public static class Vec3dAdapter extends AbstractPositionAdapter<Vec3> {

        @Override
        Vec3 create(double x, double y, double z) {
            return new Vec3(x, y, z);
        }
    }

    static abstract class AbstractVec3iAdapter<T extends Vec3i> extends TypeAdapter<T> {
        @Override
        public void write(JsonWriter out, T value) throws IOException {
            out.beginArray();
            out.value(value.getX());
            out.value(value.getY());
            out.value(value.getZ());
            out.endArray();
        }

        @Override
        public T read(JsonReader in) throws IOException {
            in.beginArray();
            int x = in.nextInt();
            int y = in.nextInt();
            int z = in.nextInt();
            in.endArray();
            return create(x, y, z);
        }

        protected abstract T create(int x, int y, int z);
    }

    public static class Vec3iAdapter extends AbstractVec3iAdapter<Vec3i> {

        @Override
        protected Vec3i create(int x, int y, int z) {
            return new Vec3i(x, y, z);
        }
    }

    public static class BlockPosAdapter extends AbstractVec3iAdapter<BlockPos> {

        @Override
        protected BlockPos create(int x, int y, int z) {
            return new BlockPos(x, y, z);
        }
    }

    public static class ChunkSectionPosAdapter extends AbstractVec3iAdapter<SectionPos> {

        @Override
        protected SectionPos create(int x, int y, int z) {
            return SectionPos.of(x, y, z);
        }
    }

    public static class IdentifierJsonAdapter extends TypeAdapter<ResourceLocation> {

        @Override
        public void write(JsonWriter out, ResourceLocation value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public ResourceLocation read(JsonReader in) throws IOException {
            String str = in.nextString();
            return ResourceLocation.parse(str);
        }
    }
}
