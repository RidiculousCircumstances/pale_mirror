package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Visible terminal block retaining the exact cargo without fabricating a replacement stack. */
record HiveNutrientTransferBlocked(SubjectId transferId, HiveNutrientTransferBlockReason reason) implements FrontierPayload {
    HiveNutrientTransferBlocked { Objects.requireNonNull(transferId, "hive nutrient transfer id"); Objects.requireNonNull(reason, "hive nutrient block reason"); }
    @Override public String type() { return "frontier.hive_nutrient_transfer_blocked"; }
}
