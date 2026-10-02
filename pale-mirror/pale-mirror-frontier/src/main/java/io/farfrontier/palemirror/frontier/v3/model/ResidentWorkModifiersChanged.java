package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Source-identified worker-stat edit, independent of nutrition and assignment. */
public record ResidentWorkModifiersChanged(SubjectId residentId, long atTick,
        ResidentWorkModifiers previous, ResidentWorkModifiers next) implements FrontierPayload {
    public ResidentWorkModifiersChanged {
        Objects.requireNonNull(residentId); Objects.requireNonNull(previous); Objects.requireNonNull(next);
        if (atTick < 0 || previous.equals(next)) throw new IllegalArgumentException("invalid work modifier change");
    }
    @Override public String type() { return "frontier.resident_work_modifiers_changed"; }
}
