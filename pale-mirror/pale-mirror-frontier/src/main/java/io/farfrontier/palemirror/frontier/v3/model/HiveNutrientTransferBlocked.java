package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Visible terminal block retaining the exact cargo without fabricating a replacement stack. */
public record HiveNutrientTransferBlocked(SubjectId transferId, HiveNutrientTransferBlockReason reason,
                                          DiagnosticTuple diagnostic) implements FrontierPayload {
    public HiveNutrientTransferBlocked { Objects.requireNonNull(transferId, "hive nutrient transfer id"); Objects.requireNonNull(reason, "hive nutrient block reason"); diagnostic = Objects.requireNonNull(diagnostic, "hive nutrient diagnostic");
        if (diagnostic.reason() != DiagnosticReason.HIVE_NUTRIENT_BLOCKED || !diagnostic.owner().id().equals(transferId)
                || !diagnostic.subject().id().equals(transferId)) throw new IllegalArgumentException("nutrient block has a foreign diagnostic tuple"); }
    @Override public String type() { return "frontier.hive_nutrient_transfer_blocked"; }
}
