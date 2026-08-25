package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code OperationStatus} lifecycle. */
public enum ReferenceOperationStatus {
    ASSEMBLING("assembling"), EN_ROUTE("en_route"), ON_STATION("on_station"), RETURNING("returning"),
    COMPLETED("completed"), ABORTED("aborted"), INTERCEPTED("intercepted"), ENGAGED("engaged");

    private final String id;

    ReferenceOperationStatus(String id) { this.id = id; }
    public String id() { return id; }
}
