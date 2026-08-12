package io.farfrontier.palemirror.api;

/** Exact capability request issued only from a persisted Core materialization operation. */
public record ThreatControllerProjection(String objectId, String jobId, VisualPoint anchor, int stage) {
    public ThreatControllerProjection {
        if (objectId == null || objectId.isBlank() || jobId == null || jobId.isBlank() || anchor == null) {
            throw new IllegalArgumentException("Threat controller projection requires provenance and an anchor");
        }
        if (stage < 1 || stage > 4) throw new IllegalArgumentException("Threat controller stage must be 1..4");
    }
}
