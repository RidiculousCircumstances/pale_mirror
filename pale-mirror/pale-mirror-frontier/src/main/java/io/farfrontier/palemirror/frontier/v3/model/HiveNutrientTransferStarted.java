package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable departure: the source stack has become one retained exact hive cargo. */
public record HiveNutrientTransferStarted(HiveNutrientTransfer transfer) implements FrontierPayload {
    public HiveNutrientTransferStarted { Objects.requireNonNull(transfer, "hive nutrient transfer"); }
    @Override public String type() { return "frontier.hive_nutrient_transfer_started"; }
}
