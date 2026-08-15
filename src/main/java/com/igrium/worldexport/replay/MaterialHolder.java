package com.igrium.worldexport.replay;

import com.igrium.worldexport.tex.ManagedNativeImage;
import com.igrium.worldexport.tex.ReplayMtl;
import com.igrium.worldexport.tex.ReplayTexture;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class MaterialHolder {
    @Getter
    private final Map<String, CompletableFuture<? extends ManagedNativeImage>> textures = new ConcurrentHashMap<>();

    private final Map<String, Map<String, ReplayMtl>> mtlLibs = new ConcurrentHashMap<>();

    public Map<String, Map<String, ReplayMtl>> getMtlLibs() {
        return Collections.unmodifiableMap(mtlLibs);
    }

    public ReplayMtl putMtl(String mtlLib, ReplayMtl mtl) {
        var lib = mtlLibs.computeIfAbsent(mtlLib, m -> new ConcurrentHashMap<>());
        return lib.put(mtl.mtl().getName(), mtl);
    }

    public void putMtlLib(String name, Collection<? extends ReplayMtl> mtls) {
        ConcurrentHashMap<String, ReplayMtl> lib = new ConcurrentHashMap<>(mtls.size());
        for (var mtl : mtls) {
            lib.put(mtl.mtl().getName(), mtl);
        }
        mtlLibs.put(name, lib);
    }

    public @Nullable ReplayMtl getMtl(String mtlLib, String name) {
        var lib = mtlLibs.get(mtlLib);
        return lib != null ? lib.get(name) : null;
    }

    public ReplayMtl getOrCreateMtl(String mtlLib, String name, Function<String, ReplayMtl> mtlSupplier) {
        var lib = mtlLibs.computeIfAbsent(mtlLib, m -> new ConcurrentHashMap<>());
        return lib.computeIfAbsent(name, n -> {
            ReplayMtl mtl = mtlSupplier.apply(name);
            if (!mtl.mtl().getName().equals(name)) {
                throw new IllegalArgumentException("Returned MTL has the wrong name.");
            }
            return mtl;
        });
    }
}
