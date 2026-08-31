package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable phase change retaining a physical arrival intent for the same exact in-transit cargo. */
public record HiveNutrientTransferEndpointPrepared(HiveNutrientTransfer transfer) implements FrontierPayload {
    public HiveNutrientTransferEndpointPrepared { Objects.requireNonNull(transfer, "hive nutrient transfer"); }
    @Override public String type() { return "frontier.hive_nutrient_transfer_endpoint_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
