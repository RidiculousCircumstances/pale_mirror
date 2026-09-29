package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One source-identified rate edit; hunger is integrated at the old rate before this edit. */
public record ResidentMetabolismChanged(SubjectId residentId, long atTick,
                                        ResidentCharacteristics previous,
                                        ResidentCharacteristics next) implements FrontierPayload {
    public ResidentMetabolismChanged {
        Objects.requireNonNull(residentId, "metabolism resident");
        Objects.requireNonNull(previous, "previous metabolism");
        Objects.requireNonNull(next, "new metabolism");
        if (!residentId.value().startsWith("resident:") || atTick < 0 || previous.equals(next))
            throw new IllegalArgumentException("metabolism edit needs one changed exact resident");
        boolean baseChanged = previous.baseMetabolismPermille() != next.baseMetabolismPermille();
        long modifierChanges = java.util.stream.Stream.concat(previous.metabolismModifiers().keySet().stream(),
                        next.metabolismModifiers().keySet().stream()).distinct()
                .filter(id -> !Objects.equals(previous.metabolismModifiers().get(id), next.metabolismModifiers().get(id)))
                .count();
        if (baseChanged && modifierChanges != 0 || !baseChanged && modifierChanges != 1)
            throw new IllegalArgumentException("metabolism edit must change one base or source modifier");
    }

    @Override public String type() { return "frontier.resident_metabolism_changed"; }
}
