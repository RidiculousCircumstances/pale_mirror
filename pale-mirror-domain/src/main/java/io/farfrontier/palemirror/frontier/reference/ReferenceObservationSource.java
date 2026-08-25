package io.farfrontier.palemirror.frontier.reference;

/** Exact typed form of Python {@code ObservationSource}. */
public enum ReferenceObservationSource {
    SETTLEMENT("settlement"),
    SCOUT("scout"),
    POST("post"),
    ROUTE("route"),
    REFUGEE("refugee"),
    TISSUE("tissue"),
    SYNAPSE("synapse"),
    SPORE("spore"),
    BIOFORM("bioform");

    private final String id;

    ReferenceObservationSource(String id) { this.id = id; }

    public String id() { return id; }
}
