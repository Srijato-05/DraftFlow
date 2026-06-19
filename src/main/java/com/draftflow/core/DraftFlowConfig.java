package com.draftflow.core;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DraftFlowConfig {
    private String version;
    private String hashAlgorithm;
    private List<String> exclude;

    public DraftFlowConfig() {
        this.version = "1.0";
        this.hashAlgorithm = "SHA-256";
        this.exclude = new ArrayList<>();
    }

    public String getVersion() {
        return version;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public List<String> getExclude() {
        return exclude != null ? exclude : new ArrayList<>();
    }

    public static DraftFlowConfig load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            return new DraftFlowConfig();
        }
        String content = Files.readString(configPath);
        return new Gson().fromJson(content, DraftFlowConfig.class);
    }
}
