package io.farfrontier.palemirror.frontier.reference;

/** Exact human-role vocabulary from Python {@code formations.HumanUnitKind}. */
public enum ReferenceHumanUnitKind {
    LINE("line"),
    SCOUT("scout"),
    ASSAULT("assault"),
    ENGINEER("engineer"),
    MEDIC("medic"),
    LOGISTICS("logistics");

    private final String id;

    ReferenceHumanUnitKind(String id) { this.id = id; }
    public String id() { return id; }
}
