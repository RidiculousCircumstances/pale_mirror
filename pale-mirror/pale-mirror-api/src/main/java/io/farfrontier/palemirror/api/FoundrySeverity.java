package io.farfrontier.palemirror.api;

/** Operator-facing impact; ERROR and BLOCKER fail a pre-publication Foundry gate. */
public enum FoundrySeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKER;

    public boolean failsGate() { return this == ERROR || this == BLOCKER; }
}
