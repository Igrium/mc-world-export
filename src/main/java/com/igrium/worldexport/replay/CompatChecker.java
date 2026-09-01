package com.igrium.worldexport.replay;

import com.google.gson.Gson;
import lombok.experimental.UtilityClass;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

@UtilityClass
public final class CompatChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldExport/CompatChecker");

    private static final CompatList compatList = CompatList.load();

    /**
     * Query and list all the loaded mods that are known to break Replay Exporter
     * @return All the mods that break; empty if there's no compatibility issues.
     */
    public static List<ModMetadata> checkModCompat() {
        List<ModMetadata> breaks = new ArrayList<>();
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            if (compatList.breaks().contains(mod.getMetadata().getId())) {
                breaks.add(mod.getMetadata());
            }
        }
        if (!breaks.isEmpty()) {
            LOGGER.warn("The following mods are known to break Replay Exporter: {}",
                    breaks.stream().map(ModMetadata::getId).toList());
        }
        return breaks;
    }

    private record CompatList(Set<String> breaks) {
        CompatList {
            breaks = breaks != null ? breaks : Collections.emptySet();
        }

        static CompatList load() {
            try (BufferedReader reader = resourceAsReader("/worldexport.compat.json")) {
                return new Gson().fromJson(reader, CompatList.class);
            } catch (Exception e) {
                LOGGER.error("Failed to load worldexport.compat.json", e);
                return new CompatList(null);
            }
        }
    }

    @SuppressWarnings("SameParameterValue") // Fuck this warning; it botches code clarity
    private static BufferedReader resourceAsReader(String resource) throws IOException {
        InputStream in = CompatChecker.class.getResourceAsStream(resource);
        if (in == null) {
            throw new FileNotFoundException(resource);
        }
        return new BufferedReader(new InputStreamReader(in));
    }

}
