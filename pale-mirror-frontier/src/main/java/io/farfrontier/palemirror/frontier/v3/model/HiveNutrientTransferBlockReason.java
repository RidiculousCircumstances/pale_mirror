package io.farfrontier.palemirror.frontier.v3.model;

/** Visible terminal reason that the same exact hive cargo cannot complete its retained corridor. */
enum HiveNutrientTransferBlockReason {
    ENDPOINT_MATERIALIZED,
    TARGET_SLOT_UNAVAILABLE,
    CARGO_CUSTODY_LOST
}
