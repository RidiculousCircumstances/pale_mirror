package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.Objects;

/** Source-identified multiplicative bonuses/debuffs; no job or movement authority. */
public record ResidentWorkModifiers(Map<SubjectId, Modifier> modifiers) {
    public record Modifier(SubjectId sourceId, HumanCapability capability, int factorPermille) {
        public Modifier {
            Objects.requireNonNull(sourceId); Objects.requireNonNull(capability);
            if (factorPermille < 100 || factorPermille > 4_000)
                throw new IllegalArgumentException("invalid work speed modifier");
        }
    }
    public ResidentWorkModifiers {
        modifiers = Map.copyOf(modifiers);
        if (modifiers.size() > 16 || modifiers.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().sourceId())))
            throw new IllegalArgumentException("invalid bounded work modifiers");
    }
    public static ResidentWorkModifiers initial() { return new ResidentWorkModifiers(Map.of()); }
    public ResidentWorkModifiers with(Modifier modifier) {
        var next = new java.util.LinkedHashMap<>(modifiers); next.put(modifier.sourceId(), modifier);
        return new ResidentWorkModifiers(next);
    }
    public ResidentWorkModifiers without(SubjectId source) {
        if (!modifiers.containsKey(source)) throw new IllegalArgumentException("unknown work modifier");
        var next = new java.util.LinkedHashMap<>(modifiers); next.remove(source);
        return new ResidentWorkModifiers(next);
    }
}
