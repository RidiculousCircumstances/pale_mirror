package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashSet;

/** Single-publication bridge from collision recovery to exact actor hand-off capture. */
final class SourceGrayboxActorRecoveryCapture {
    private final LinkedHashSet<String> recovered = new LinkedHashSet<>();

    void beginPublication() {
        recovered.clear();
    }

    void record(String id, String kind) {
        recovered.add(SourceGrayboxMaterializer.entityKey(id, kind));
    }

    boolean hasAny() {
        return !recovered.isEmpty();
    }

    boolean consume(String id, String kind) {
        return recovered.remove(SourceGrayboxMaterializer.entityKey(id, kind));
    }
}
