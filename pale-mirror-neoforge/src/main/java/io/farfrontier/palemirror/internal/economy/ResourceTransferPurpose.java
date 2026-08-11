package io.farfrontier.palemirror.internal.economy;

/** Physical receipt destination; only settlement stock is freely withdrawable. */
public enum ResourceTransferPurpose {
    SETTLEMENT_STOCK,
    DEVELOPMENT_PROJECT
}
