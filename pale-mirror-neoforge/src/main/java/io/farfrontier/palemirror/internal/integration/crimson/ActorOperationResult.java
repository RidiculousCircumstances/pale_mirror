package io.farfrontier.palemirror.internal.integration.crimson;

/** Typed result of a sandbox-owned actor operation; failures stay optional. */
public record ActorOperationResult(Status status, String diagnostic) {
    public enum Status { MATERIALIZED, UNAVAILABLE }

    public ActorOperationResult {
        if (diagnostic == null) diagnostic = "";
    }

    public static ActorOperationResult materialized() { return new ActorOperationResult(Status.MATERIALIZED, ""); }
    public static ActorOperationResult unavailable(String diagnostic) { return new ActorOperationResult(Status.UNAVAILABLE, diagnostic); }
}
