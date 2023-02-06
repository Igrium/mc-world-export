package org.scaffoldeditor.worldexport.vcap;

import java.util.BitSet;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

/**
 * The complete model data of a blockstate.
 */
public record BlockModelEntry(BakedModel model, BlockState blockState, byte faces, boolean transparent, boolean emissive) {

    public boolean isFaceVisible(int id) {
        if (id < 0 || id >= 6) {
            throw new IndexOutOfBoundsException(id);
        }

        return ((faces >> id) & 1) == 1;
    }

    public boolean isFaceVisible(Direction direction) {
        return isFaceVisible(direction.getId());
    }

    /**
     * Get the model ID that this entry will save with.
     * @return The model ID.
     */
    public String getID() {
        Identifier id = Registries.BLOCK.getId(blockState.getBlock());
        int stateId = Block.getRawIdFromState(blockState);
        return id.toUnderscoreSeparatedString() + "#" + Integer.toHexString(stateId) + "." + Integer.toHexString(faces);
    }

    /**
     * A simplified builder for ModelEntry.
     */
    public static class Builder {
        private BakedModel model;
        private BlockState blockState;
        private BitSet faces = new BitSet(8);
        private boolean transparent;
        private boolean emissive;

        /**
         * Create a model entry builder.
         * @param model The block model to use.
         * @param blockState The block state to use.
         */
        public Builder(BakedModel model, BlockState blockState) {
            this.model = model;
            this.blockState = blockState;
        }

        /**
         * Apply this builder and create a model entry.
         * @return The built model entry.
         */
        public BlockModelEntry build() {
            byte faces;
            if (this.faces.isEmpty()) {
                faces = 0;
            } else {
                faces = this.faces.toByteArray()[0];
            }
            return new BlockModelEntry(model, blockState, faces, transparent, emissive);
        }

        /**
         * Set the block model.
         * @param model Block model to use.
         * @return <code>this</code>
         */
        public Builder model(BakedModel model) {
            this.model = model;
            return this;
        }

        /**
         * Set the block state.
         * @param blockState Block state to use.
         * @return <code>this</code>
         */
        public Builder blockState(BlockState blockState) {
            this.blockState = blockState;
            return this;
        }
        
        /**
         * Set the transparency flag.
         * @param transparent Whether this block is transparent.
         * @return <code>this</code>
         */
        public Builder transparent(boolean transparent) {
            this.transparent = transparent;
            return this;
        }

        /**
         * Set the emissive flag.
         * @param emissive Whether this block emits light.
         * @return <code>this</code>
         */
        public Builder emissive(boolean emissive) {
            this.emissive = emissive;
            return this;
        }

        /**
         * Set the face visibility flags of this entry.
         * 
         * @param faces  An array containing the face visibility flags, in the order
         *               they're defined in the {@link Direction} enum.
         * @param offset The offset in the array to start reading. The array must have
         *               at least 6 elements starting at this offset.
         * @return <code>this</code>
         */
        public Builder faces(boolean[] faces, int offset) {
            if (faces.length <= offset + 6) {
                throw new IllegalArgumentException("This array isn't long enough for the given offset.");
            }
            for (int i = offset; i < 6; i++) {
                this.faces.set(i, faces[i]);
            }
            return this;
        }

        /**
         * Set the face visibility flag of this entry.
         * @param faces A byte containing the bits representing the face visibility flags.
         * @return <code>this</code>
         */
        public Builder faces(byte faces) {
            this.faces = BitSet.valueOf(new byte[] { faces });
            return this;
        }

        /**
         * Set the visibility of a particular face.
         * @param id The direction id of the face.
         * @param visible Whether it's visible.
         * @return <code>this</code>
         */
        public Builder face(int id, boolean visible) {
            if (id < 0 || id >= 6) {
                throw new IndexOutOfBoundsException(id);
            }
            this.faces.set(id, visible);
            return this;
        }
        
        /**
         * Set the visibility of a particular face.
         * @param direction The direction of the face.
         * @param visible Whether it's visible.
         * @return <code>this</code>
         */
        public Builder face(Direction direction, boolean visible) {
            return face(direction.getId(), visible);
        }
    }
}
