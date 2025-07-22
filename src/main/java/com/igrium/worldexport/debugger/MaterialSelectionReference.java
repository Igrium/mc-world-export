package com.igrium.worldexport.debugger;

import de.javagl.obj.Mtl;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record MaterialSelectionReference(String mtlLib, int index) {
    @Nullable
    public Mtl get(Map<? super String, ? extends List<? extends Mtl>> mtls) {
        var mtlList = mtls.get(mtlLib);
        if (mtlList != null) {
            if (index <= mtls.size()) {
                return mtlList.get(index);
            }
        }
        return null;
    }

    public static final MaterialSelectionReference EMPTY = new MaterialSelectionReference("", 0);
}
