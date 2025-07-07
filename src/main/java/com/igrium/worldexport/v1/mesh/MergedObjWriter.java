package com.igrium.worldexport.v1.mesh;

import de.javagl.obj.ObjWriter;
import de.javagl.obj.ReadableObj;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MergedObjWriter {

    /**
     * Write a set of objs to a file, where each obj is one object.
     * @param objs A map of object names with their mesh data.
     * @param writer Writer to write to.
     */
    public static void writeObjects(@NotNull Map<? extends String, ? extends ReadableObj> objs, @NotNull Writer writer) throws IOException {
        if (objs.isEmpty())
            return;

        List<String> mtlFileNames = new ArrayList<>();
        for (var obj : objs.values()) {
            mtlFileNames.addAll(obj.getMtlFileNames());
        }

        if (!mtlFileNames.isEmpty())
        {
            writer.write("mtllib ");
            for (int i=0; i<mtlFileNames.size(); i++)
            {
                if (i > 0)
                {
                    writer.write(" ");
                }
                writer.write(mtlFileNames.get(i));
            }
            writer.write("\n");
        }

        for (var entry : objs.entrySet()) {
            writer.write("o " + entry.getKey());
            writer.write("\n");
            ObjWriter.write(entry.getValue(), writer);
        }
    }
}
