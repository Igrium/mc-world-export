package com.igrium.worldexport.compat.imgui;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Call the compat mod's native file dialog implementation
 */
public interface FileDialogCallback {

    /**
     * A type of file that may be accepted by a file dialog.
     * @param name The name of the file type. ex: "JPEG File".
     * @param extensions The file extensions this filter supports. ex: ["jpg", "jpeg"]
     * @see javax.swing.filechooser.FileNameExtensionFilter
     */
    record FileFilter(String name, String... extensions) {
    }

    CompletableFuture<Optional<Path>> showSaveDialog(@Nullable Path defaultPath,
                                                            @Nullable String defaultName,
                                                            FileFilter... filters);
}
