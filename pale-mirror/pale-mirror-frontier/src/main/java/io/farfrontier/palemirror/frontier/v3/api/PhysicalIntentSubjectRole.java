package io.farfrontier.palemirror.frontier.v3.api;

/**
 * Stable named semantic positions in a {@link PhysicalIntentRoleBinding}.  These tags are
 * deliberately independent of declaration order; retired values must never be reused.
 */
public enum PhysicalIntentSubjectRole {
    OWNER(1), SITE(2), JOB(3), WORKER(4), INPUT_ITEM(5), OUTPUT_ITEM(6),
    CONTRACT(7), CARGO(8), SOURCE_ITEM(9), PROJECT(10), CARGO_ITEM(11),
    TRANSFER(12), TARGET_ITEM(13), ASSAULT(14), DEFENDER(15), EQUIPMENT(16),
    ROUTE(17), OPERATION(18), FACILITY(19), STRUCTURE(20), MATERIAL(21),
    BOMBER(22), ENGAGEMENT(23), ATTACKER(24), TARGET(25), NEST(26), ITEM(27);

    private final int wireTag;
    PhysicalIntentSubjectRole(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }

    public static PhysicalIntentSubjectRole fromWire(int wireTag) {
        for (PhysicalIntentSubjectRole role : values()) if (role.wireTag == wireTag) return role;
        throw new IllegalArgumentException("unknown physical intent subject-role tag: " + wireTag);
    }
}
