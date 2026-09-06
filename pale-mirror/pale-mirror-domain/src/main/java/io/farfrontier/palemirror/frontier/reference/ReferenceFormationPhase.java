package io.farfrontier.palemirror.frontier.reference;

/** Source formation phase, retained for materialization and operation parity. */
public enum ReferenceFormationPhase {
    SCREEN("screen"), MAIN_ACTION("main_action"), SECURE("secure"), WITHDRAW("withdraw"), RETURN("return");

    private final String id;
    ReferenceFormationPhase(String id) { this.id = id; }
    public String id() { return id; }
}
