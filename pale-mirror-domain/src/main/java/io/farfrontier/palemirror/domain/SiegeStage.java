package io.farfrontier.palemirror.domain;

/** PM-owned clearance gate for an APEX threat site; it never mirrors Crimson phases. */
public enum SiegeStage {
    INACTIVE,
    PENDING,
    BYPASSED,
    NODES,
    BOSS,
    BLOODLINK_I,
    BLOODLINK_II,
    BLOODLINK_III,
    CONTROLLER_VULNERABLE;

    public boolean protectsController() {
        return this == PENDING || this == NODES || this == BOSS || this == BLOODLINK_I
                || this == BLOODLINK_II || this == BLOODLINK_III;
    }
}
