package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One persisted COLD cursor advance through a named hive nutrient corridor. */
record HiveNutrientTransferAdvanced(SubjectId transferId, int cursor) implements FrontierPayload {
    HiveNutrientTransferAdvanced { Objects.requireNonNull(transferId, "hive nutrient transfer id"); if (cursor < 1) throw new IllegalArgumentException("hive nutrient cursor must advance"); }
    @Override public String type() { return "frontier.hive_nutrient_transfer_advanced"; }
}
