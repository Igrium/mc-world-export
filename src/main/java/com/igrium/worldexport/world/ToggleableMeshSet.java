package com.igrium.worldexport.world;

import de.javagl.obj.Obj;
import it.unimi.dsi.fastutil.ints.Int2BooleanAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanSortedMap;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A collection of obj files with keyframes to be toggled on and off.
 */
public class ToggleableMeshSet {
    public static class ObjInfo {

        @Getter
        private final Obj obj;

        private ObjInfo(Obj obj) {
            this.obj = obj;
        }

        private final Int2BooleanSortedMap keyframes = new Int2BooleanAVLTreeMap();

        public void addKeyframe(int timestamp, boolean value) {
            keyframes.put(timestamp, value);
        }

        public Iterable<Int2BooleanMap.Entry> getKeyframes() {
            return keyframes.int2BooleanEntrySet();
        }
    }


    private final Map<String, Obj> objs = new ConcurrentHashMap<>();
}
