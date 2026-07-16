/**
 * @file DraftFlowConfig.java
 * @description Configuration properties manager for DraftFlow VCS.
 * Defines configuration schemas for local repositories, mapping version numbers, hash specifications,
 * file exclusion rules, and LFS threshold settings.
 * 
 * DESIGN RATIONALE:
 * - Employs lightweight JSON serialization via Gson to read and write `.draftflow/config.json`.
 * - Defaults exclusions to empty and sets standard LFS filters (e.g. zip, pdf, png, etc.)
 *   with a 10MB size limit to preserve system health dynamically out-of-the-box.
 */

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
    private List<String> lfsExtensions;
    private Long lfsSizeThreshold;

    public DraftFlowConfig() {
        this.version = "1.0";
        this.hashAlgorithm = "SHA-256";
        this.exclude = new ArrayList<>();
        this.lfsExtensions = java.util.Arrays.asList(".png", ".zip", ".mp4", ".pdf", ".tgz", ".gz", ".exe", ".tar", ".dmg", ".bin", ".wav", ".mp3");
        this.lfsSizeThreshold = 10L * 1024 * 1024;
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

    public List<String> getLfsExtensions() {
        return lfsExtensions != null ? lfsExtensions : new ArrayList<>();
    }

    public Long getLfsSizeThreshold() {
        return lfsSizeThreshold;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    public void setExclude(List<String> exclude) {
        this.exclude = exclude;
    }

    public void setLfsExtensions(List<String> lfsExtensions) {
        this.lfsExtensions = lfsExtensions;
    }

    public void setLfsSizeThreshold(Long lfsSizeThreshold) {
        this.lfsSizeThreshold = lfsSizeThreshold;
    }

    public static DraftFlowConfig load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            return new DraftFlowConfig();
        }
        String content = Files.readString(configPath);
        return new Gson().fromJson(content, DraftFlowConfig.class);
    }
}
