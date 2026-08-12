package io.farfrontier.palemirror.api;

import java.util.UUID;

public record ThreatControllerResult(Status status, UUID entityId, String diagnostic) {
    public enum Status { MATERIALIZED, ABSENT, BLOCKED }
    public ThreatControllerResult { diagnostic = diagnostic == null ? "" : diagnostic; }
    public static ThreatControllerResult materialized(UUID id) { return new ThreatControllerResult(Status.MATERIALIZED, id, ""); }
    public static ThreatControllerResult absent() { return new ThreatControllerResult(Status.ABSENT, null, ""); }
    public static ThreatControllerResult blocked(String reason) { return new ThreatControllerResult(Status.BLOCKED, null, reason); }
}
