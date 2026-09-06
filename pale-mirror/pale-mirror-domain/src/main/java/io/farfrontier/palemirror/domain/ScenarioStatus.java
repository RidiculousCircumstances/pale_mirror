package io.farfrontier.palemirror.domain;

public enum ScenarioStatus {
    OFFERED,
    INVESTIGATE,
    RECOVER,
    ASSESS,
    RESPOND,
    RESOLVED,
    DECLINED,
    EXPIRED,
    FAILED,
    CANCELLED,
    BLOCKED;

    public boolean isTerminal() {
        return this == RESOLVED || this == DECLINED || this == EXPIRED || this == FAILED || this == CANCELLED;
    }
}
