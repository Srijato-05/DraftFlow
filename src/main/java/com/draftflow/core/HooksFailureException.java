package com.draftflow.core;

import java.util.List;

public class HooksFailureException extends DraftFlowException {
    public HooksFailureException(String message) {
        super("HOOKS_FAILURE", message);
    }

    public HooksFailureException(String message, Throwable cause) {
        super("HOOKS_FAILURE", message, cause);
    }

    public HooksFailureException(String message, List<String> suggestions) {
        super("HOOKS_FAILURE", message, suggestions);
    }

    public HooksFailureException(String message, List<String> suggestions, Throwable cause) {
        super("HOOKS_FAILURE", message, suggestions, cause);
    }
}
