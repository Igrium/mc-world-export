package com.igrium.worldexport.replay;

import com.igrium.worldexport.tex.ReplayTexture;
import de.javagl.obj.Mtl;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class MaterialHolder {
    @Getter
    private final Map<String, CompletableFuture<? extends ReplayTexture>> textures = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Mtl>> mtlLibs = new ConcurrentHashMap<>();

    public Map<String, Map<String, Mtl>> getMtlLibs() {
        return Collections.unmodifiableMap(mtlLibs);
    }

    public Mtl putMtl(String mtlLib, Mtl mtl) {
        var lib = mtlLibs.computeIfAbsent(mtlLib, m -> new ConcurrentHashMap<>());
        return lib.put(mtl.getName(), mtl);
    }

    public void putMtlLib(String name, Collection<? extends Mtl> mtls) {
        ConcurrentHashMap<String, Mtl> lib = new ConcurrentHashMap<>(mtls.size());
        for (var mtl : mtls) {
            lib.put(mtl.getName(), mtl);
        }
        mtlLibs.put(name, lib);
    }

    public @Nullable Mtl getMtl(String mtlLib, String name) {
        var lib = mtlLibs.get(mtlLib);
        return lib != null ? lib.get(name) : null;
    }

    public Mtl getOrCreateMtl(String mtlLib, String name, Function<String, Mtl> mtlSupplier) {
        var lib = mtlLibs.computeIfAbsent(mtlLib, m -> new ConcurrentHashMap<>());
        return lib.computeIfAbsent(name, n -> {
            Mtl mtl = mtlSupplier.apply(name);
            if (!mtl.getName().equals(name)) {
                throw new IllegalArgumentException("Returned MTL has the wrong name.");
            }
            return mtl;
        });
    }
}
