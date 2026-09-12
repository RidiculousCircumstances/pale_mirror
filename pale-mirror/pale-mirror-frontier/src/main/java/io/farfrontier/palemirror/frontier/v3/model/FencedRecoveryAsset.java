package io.farfrontier.palemirror.frontier.v3.model;

/** The finite physical families whose late projections require an epoch fence. */
public enum FencedRecoveryAsset {
    BODY(1), CARGO(2), CONTAINER(3), EFFECT(4);

    private final int wireTag;
    FencedRecoveryAsset(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
}
