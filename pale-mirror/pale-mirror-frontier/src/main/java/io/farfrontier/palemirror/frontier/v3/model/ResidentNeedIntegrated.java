package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One due-time integration of one exact resident's hunger, not a population scan. */
public record ResidentNeedIntegrated(SubjectId residentId, long atTick,
                                     int previousDay, int integratedDay,
                                     int hungerDeficit) implements FrontierPayload {
    public ResidentNeedIntegrated {
        Objects.requireNonNull(residentId, "need resident");
        if (!residentId.value().startsWith("resident:") || atTick < 0
                || previousDay < 0 || integratedDay <= previousDay
                || hungerDeficit < 1 || hungerDeficit > ResidentNutrition.MAX_HUNGER_UNITS)
            throw new IllegalArgumentException("resident need integration lacks a valid exact due boundary");
    }

    @Override public String type() { return "frontier.resident_need_integrated"; }
}
