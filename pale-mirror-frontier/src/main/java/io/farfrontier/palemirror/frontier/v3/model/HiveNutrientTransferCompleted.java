package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable terminal receipt after the same exact cargo enters its retained destination slot. */
record HiveNutrientTransferCompleted(HiveNutrientReceipt receipt) implements FrontierPayload {
    HiveNutrientTransferCompleted { Objects.requireNonNull(receipt, "hive nutrient receipt"); }
    @Override public String type() { return "frontier.hive_nutrient_transfer_completed"; }
}
