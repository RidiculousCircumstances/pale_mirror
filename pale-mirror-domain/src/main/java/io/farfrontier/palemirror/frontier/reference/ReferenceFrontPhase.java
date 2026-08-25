package io.farfrontier.palemirror.frontier.reference;

/** Exact typed lifecycle for Python {@code FrontPhase}. */
public enum ReferenceFrontPhase {
    RECON("recon"),
    ASSEMBLE("assemble"),
    ESTABLISH("establish"),
    CORDON("cordon"),
    CLEAR("clear"),
    HOLD("hold"),
    RESTORE("restore"),
    WITHDRAW("withdraw"),
    COMPLETE("complete"),
    FAILED("failed");

    private final String id;

    ReferenceFrontPhase(String id) { this.id = id; }

    public String id() { return id; }

    public boolean terminal() { return this == COMPLETE || this == FAILED; }
}
