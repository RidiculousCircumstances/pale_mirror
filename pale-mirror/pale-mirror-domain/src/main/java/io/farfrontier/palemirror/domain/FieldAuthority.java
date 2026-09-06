package io.farfrontier.palemirror.domain;

/** Declares which side owns or contributes a settlement field. */
public enum FieldAuthority {
    PM_OWNED,
    NATIVE_OWNED,
    DERIVED,
    OBSERVED_ONLY,
    RECONCILED
}
