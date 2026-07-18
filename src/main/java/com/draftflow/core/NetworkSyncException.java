package com.draftflow.core;

import java.util.List;

public class NetworkSyncException extends DraftFlowException {
    public NetworkSyncException(String message) {
        super("NETWORK_SYNC_FAILURE", message);
    }

    public NetworkSyncException(String message, Throwable cause) {
        super("NETWORK_SYNC_FAILURE", message, cause);
    }

    public NetworkSyncException(String message, List<String> suggestions) {
        super("NETWORK_SYNC_FAILURE", message, suggestions);
    }

    public NetworkSyncException(String message, List<String> suggestions, Throwable cause) {
        super("NETWORK_SYNC_FAILURE", message, suggestions, cause);
    }
}
