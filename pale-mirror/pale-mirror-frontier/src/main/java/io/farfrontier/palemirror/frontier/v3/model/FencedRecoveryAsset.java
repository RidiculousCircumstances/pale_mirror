package io.farfrontier.palemirror.frontier.v3.model;

/** The finite physical families whose late projections require an epoch fence. */
public enum FencedRecoveryAsset {
    BODY(1, ActorBodyAuthority::mayCompactRetirement),
    CARGO(2, (retired, successor) -> true),
    CONTAINER(3, (retired, successor) -> true),
    EFFECT(4, (retired, successor) -> true);

    @FunctionalInterface private interface Retention {
        boolean mayCompact(FencedRecoveryTombstone retired, java.util.Optional<FencedRecoveryBinding> successor);
    }
    private final int wireTag;
    private final Retention retention;
    FencedRecoveryAsset(int wireTag, Retention retention) {
        this.wireTag = wireTag; this.retention = java.util.Objects.requireNonNull(retention);
    }
    boolean mayCompact(FencedRecoveryTombstone retired, java.util.Optional<FencedRecoveryBinding> successor) {
        if (retired.asset() != this) throw new IllegalArgumentException("retirement capability has a foreign asset");
        return retention.mayCompact(retired, successor);
    }
    public int wireTag() { return wireTag; }
}
