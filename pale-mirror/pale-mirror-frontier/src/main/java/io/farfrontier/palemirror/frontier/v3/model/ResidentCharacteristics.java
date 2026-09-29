package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded, typed physiological state of one resident. Skills and profession are separate. */
public record ResidentCharacteristics(int version, int baseMetabolismPermille,
                                      Map<SubjectId, MetabolismModifier> metabolismModifiers) {
    public static final int VERSION = 1;
    public static final int DEFAULT_METABOLISM_PERMILLE = 1_000;
    public static final int MIN_METABOLISM_PERMILLE = 250;
    public static final int MAX_METABOLISM_PERMILLE = 4_000;
    public static final int MAX_MODIFIERS = 16;

    /** Explicitly retained until a source-identified removal event; no hidden timer. */
    public record MetabolismModifier(SubjectId sourceId, int deltaPermille) {
        public MetabolismModifier {
            Objects.requireNonNull(sourceId, "characteristic modifier source");
            if (deltaPermille == 0 || Math.abs((long) deltaPermille) > MAX_METABOLISM_PERMILLE)
                throw new IllegalArgumentException("invalid metabolism modifier");
        }
    }

    public ResidentCharacteristics {
        if (version != VERSION || baseMetabolismPermille < MIN_METABOLISM_PERMILLE
                || baseMetabolismPermille > MAX_METABOLISM_PERMILLE)
            throw new IllegalArgumentException("unsupported resident characteristic version or base metabolism");
        Objects.requireNonNull(metabolismModifiers, "metabolism modifiers");
        if (metabolismModifiers.size() > MAX_MODIFIERS) throw new IllegalArgumentException("too many metabolism modifiers");
        Map<SubjectId, MetabolismModifier> ordered = new LinkedHashMap<>();
        metabolismModifiers.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (!entry.getKey().equals(entry.getValue().sourceId()))
                        throw new IllegalArgumentException("modifier key differs from its source");
                    ordered.put(entry.getKey(), entry.getValue());
                });
        metabolismModifiers = Map.copyOf(ordered);
    }

    public static ResidentCharacteristics initial() {
        return new ResidentCharacteristics(VERSION, DEFAULT_METABOLISM_PERMILLE, Map.of());
    }

    public static ResidentCharacteristics initial(FrontierRuleset.ResidentLife rules) {
        Objects.requireNonNull(rules, "resident characteristic rules");
        return new ResidentCharacteristics(VERSION, rules.metabolismDefaultPermille(), Map.of());
    }

    public int effectiveMetabolismPermille(long tick) {
        if (tick < 0) throw new IllegalArgumentException("characteristic instant must be non-negative");
        long total = baseMetabolismPermille;
        for (MetabolismModifier modifier : metabolismModifiers.values().stream()
                .sorted(Comparator.comparing(MetabolismModifier::sourceId)).toList())
            total = Math.addExact(total, modifier.deltaPermille());
        return (int) Math.clamp(total, MIN_METABOLISM_PERMILLE, MAX_METABOLISM_PERMILLE);
    }

    public ResidentCharacteristics withBaseMetabolism(int value) {
        return new ResidentCharacteristics(version, value, metabolismModifiers);
    }

    public ResidentCharacteristics withModifier(MetabolismModifier modifier) {
        Map<SubjectId, MetabolismModifier> next = new LinkedHashMap<>(metabolismModifiers);
        next.put(modifier.sourceId(), modifier);
        return new ResidentCharacteristics(version, baseMetabolismPermille, next);
    }

    public ResidentCharacteristics withoutModifier(SubjectId sourceId) {
        if (!metabolismModifiers.containsKey(sourceId)) throw new IllegalArgumentException("unknown metabolism modifier source");
        Map<SubjectId, MetabolismModifier> next = new LinkedHashMap<>(metabolismModifiers);
        next.remove(sourceId);
        return new ResidentCharacteristics(version, baseMetabolismPermille, next);
    }
}
