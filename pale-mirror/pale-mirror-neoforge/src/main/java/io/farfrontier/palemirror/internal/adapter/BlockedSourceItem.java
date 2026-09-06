package io.farfrontier.palemirror.internal.adapter;

/** Adapter-provided diagnosis for a source-owned item outside PM's current product scope. */
public record BlockedSourceItem(String sourceId, String fingerprint) {
    public BlockedSourceItem {
        if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("sourceId");
        if (fingerprint == null || fingerprint.isBlank()) throw new IllegalArgumentException("fingerprint");
    }
}
