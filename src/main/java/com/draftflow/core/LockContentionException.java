package com.draftflow.core;

import java.util.List;

public class LockContentionException extends DraftFlowException {
    public LockContentionException(String message) {
        super("LOCK_CONTENTION", message);
    }

    public LockContentionException(String message, Throwable cause) {
        super("LOCK_CONTENTION", message, cause);
    }

    public LockContentionException(String message, List<String> suggestions) {
        super("LOCK_CONTENTION", message, suggestions);
    }

    public LockContentionException(String message, List<String> suggestions, Throwable cause) {
        super("LOCK_CONTENTION", message, suggestions, cause);
    }
}
