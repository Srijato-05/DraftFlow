package com.draftflow.core;

import java.util.List;

public class CASCorruptException extends DraftFlowException {
    public CASCorruptException(String message) {
        super("CAS_CORRUPTION", message);
    }

    public CASCorruptException(String message, Throwable cause) {
        super("CAS_CORRUPTION", message, cause);
    }

    public CASCorruptException(String message, List<String> suggestions) {
        super("CAS_CORRUPTION", message, suggestions);
    }

    public CASCorruptException(String message, List<String> suggestions, Throwable cause) {
        super("CAS_CORRUPTION", message, suggestions, cause);
    }
}
