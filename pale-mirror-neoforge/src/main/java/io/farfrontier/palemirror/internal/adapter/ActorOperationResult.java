package io.farfrontier.palemirror.internal.adapter;

/** Typed outcome of source-adapter work; canonical PM state never depends on it. */
public record ActorOperationResult(Status status, String diagnostic) {
    public enum Status { MATERIALIZED, UNAVAILABLE }

    public ActorOperationResult {
        if (diagnostic == null) diagnostic = "";
    }

    public static ActorOperationResult materialized() { return new ActorOperationResult(Status.MATERIALIZED, ""); }
    public static ActorOperationResult unavailable(String diagnostic) { return new ActorOperationResult(Status.UNAVAILABLE, diagnostic); }
}
