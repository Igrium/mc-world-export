package com.igrium.replay_debugger.ui;

import com.igrium.replay_debugger.ReplayParseException;

public interface ParsingUpdateListener {
    void setProgress(float progress);
    void setInfoText(String text);
    
    default void handle(ReplayParseException e) {
        throw e;
    }
}
