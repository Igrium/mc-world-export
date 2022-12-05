package org.scaffoldeditor.worldexport.util;

import java.io.IOException;

public class UnsupportedFileTypeException extends IOException {
    private final String extension;

    public UnsupportedFileTypeException(String extension) {
        super("Unsupported file type: "+extension);
        this.extension = extension;
    }

    public UnsupportedFileTypeException(String message, String extension) {
        super(message);
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }
}
