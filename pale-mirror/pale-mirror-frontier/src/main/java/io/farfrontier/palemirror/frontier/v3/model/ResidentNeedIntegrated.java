package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One due-time, fixed-point integration of one exact resident's bounded satiety. */
public record ResidentNeedIntegrated(SubjectId residentId, long atTick,
                                     long previousTick, int satietyUnits,
                                     long fractionalProgress) implements FrontierPayload {
    public ResidentNeedIntegrated {
        Objects.requireNonNull(residentId, "need resident");
        if (!residentId.value().startsWith("resident:") || previousTick < 0 || atTick <= previousTick
                || satietyUnits < 0 || satietyUnits > ResidentNutrition.MAX_SATIETY_UNITS
                || fractionalProgress < 0)
            throw new IllegalArgumentException("resident need integration lacks one exact elapsed interval");
    }

    @Override public String type() { return "frontier.resident_need_integrated"; }
}
