package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code FieldModuleKind} vocabulary. */
public enum ReferenceFieldModuleKind {
    DEPOT("depot"),
    FIELD_HOSPITAL("field_hospital"),
    FIRE_SUPPORT("fire_support"),
    DECONTAMINATION("decontamination"),
    FORTIFICATION("fortification");

    private final String id;

    ReferenceFieldModuleKind(String id) { this.id = id; }
    public String id() { return id; }
}
