package com.draftflow.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DraftFlowException extends RuntimeException {
    private final String errorCode;
    private final List<String> suggestions;

    public DraftFlowException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.suggestions = new ArrayList<>();
    }

    public DraftFlowException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.suggestions = new ArrayList<>();
    }

    public DraftFlowException(String errorCode, String message, List<String> suggestions) {
        super(message);
        this.errorCode = errorCode;
        this.suggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
    }

    public DraftFlowException(String errorCode, String message, List<String> suggestions, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.suggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<String> getSuggestions() {
        return Collections.unmodifiableList(suggestions);
    }
}
