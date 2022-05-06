package com.igrium.replay_debugger;

public class ReplayParseException extends RuntimeException {
    enum ParseStage {
        GENERAL,
        ENTITY,
        MATERIAL,
        TEXTURE
    }

    private final ParseStage stage;
    private final String culprit;

    public ReplayParseException() {
        this.stage = ParseStage.GENERAL;
        this.culprit = null;
    }

    public ReplayParseException(String message) {
        super(message);
        this.stage = ParseStage.GENERAL;
        this.culprit = null;
    }

    public ReplayParseException(String message, Throwable cause) {
        super(message, cause);
        this.stage = ParseStage.GENERAL;
        this.culprit = null;
    }

    public ReplayParseException(ParseStage stage, String culprit) {
        super(getDefaultMessage(stage, culprit));
        this.stage = stage;
        this.culprit = culprit;
    }

    public ReplayParseException(ParseStage stage, String culprit, Throwable cause) {
        super(getDefaultMessage(stage, culprit), cause);
        this.stage = stage;
        this.culprit = culprit;
    }
    
    public ReplayParseException(ParseStage stage, String culprit, String message) {
        super(message);
        this.stage = stage;
        this.culprit = culprit;
    }

    public ReplayParseException(ParseStage stage, String culprit, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.culprit = culprit;
    }

    public ParseStage getStage() {
        return stage;
    }

    public String getCulprit() {
        return culprit;
    }

    public static String getDefaultMessage(ParseStage stage, String culprit) {
        if (stage == ParseStage.ENTITY) {
            return culprit == null ? "Error parsing entity." : "Error parsing entity: "+culprit;
        } else if (stage == ParseStage.MATERIAL) {
            return culprit == null ? "Error parsing material." : "Error parsing material: "+culprit;
        } else if (stage == ParseStage.TEXTURE) {
            return culprit == null ? "Error parsing texture." : "Error parsing texture: "+culprit;
        } else {
            return culprit == null ? "Error parsing replay file" : "Error parsing "+culprit;
        }
    }
}
