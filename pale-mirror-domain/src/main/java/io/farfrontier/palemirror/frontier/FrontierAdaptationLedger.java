package io.farfrontier.palemirror.frontier;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Bounded, deterministic replacement for the reference model's global genome and damage memory. */
final class FrontierAdaptationLedger {
    static final long GENETIC_SCALE = 1_000;
    private static final long BASE_COST = 12 * GENETIC_SCALE;
    private static final int MAX_LEVEL = 2;
    private final EnumMap<FrontierAdaptation, Integer> levels = new EnumMap<>(FrontierAdaptation.class);
    private final EnumMap<FrontierDamageKind, Long> damageMemory = new EnumMap<>(FrontierDamageKind.class);

    Map<FrontierAdaptation, Integer> levels() { return Map.copyOf(levels); }
    Map<FrontierDamageKind, Long> memory() { return Map.copyOf(damageMemory); }
    int level(FrontierAdaptation adaptation) { return levels.getOrDefault(adaptation, 0); }
    void record(FrontierDamageKind kind) { damageMemory.merge(kind, 1_000L, (left, right) -> Math.min(100_000L, left + right)); }
    void restore(Map<FrontierAdaptation, Integer> restoredLevels, Map<FrontierDamageKind, Long> restoredMemory) {
        levels.clear(); damageMemory.clear();
        restoredLevels.forEach((kind, value) -> {
            if (kind == null || value == null || value < 1 || value > MAX_LEVEL) throw new IllegalArgumentException("invalid adaptation level");
            levels.put(kind, value);
        });
        restoredMemory.forEach((kind, value) -> {
            if (kind == null || value == null || value < 1 || value > 100_000L) throw new IllegalArgumentException("invalid adaptation memory");
            damageMemory.put(kind, value);
        });
    }
    void advance(FrontierWorldState state, String causationId, List<FrontierEvent> events) {
        damageMemory.replaceAll((kind, value) -> value * 94 / 100);
        damageMemory.entrySet().removeIf(entry -> entry.getValue() < 30);
        if (state.day() % 10 != 0) return;
        long total = state.hives().stream().mapToLong(FrontierHive::geneticMaterial).sum();
        if (total < BASE_COST) return;
        FrontierDamageKind pressure = damageMemory.entrySet().stream().max(Comparator
                .<Map.Entry<FrontierDamageKind, Long>, Long>comparing(Map.Entry::getValue)
                .thenComparingInt(entry -> entry.getKey().ordinal()))
                .map(Map.Entry::getKey).orElse(FrontierDamageKind.STARVATION);
        FrontierAdaptation adaptation = responseTo(pressure);
        if (level(adaptation) >= MAX_LEVEL) return;
        long cost = variedCost(state.seed(), state.day());
        if (total < cost) return;
        long remaining = cost;
        for (FrontierHive donor : state.hives().stream().sorted(Comparator.comparingLong(FrontierHive::geneticMaterial).reversed()
                .thenComparing(FrontierHive::id)).toList()) {
            long paid = Math.min(donor.geneticMaterial(), remaining);
            if (paid > 0) donor.spendGeneticMaterial(paid);
            remaining -= paid;
            if (remaining == 0) break;
        }
        if (remaining != 0) throw new IllegalStateException("adaptation payment changed during deterministic resolution");
        levels.merge(adaptation, 1, Integer::sum);
        events.add(state.event(FrontierEvent.Type.ADAPTATION_ACQUIRED, adaptation.name(), causationId));
    }
    int multiplier(FrontierAdaptation adaptation, int perLevelThousandths) {
        return Math.addExact(1_000, Math.multiplyExact(level(adaptation), perLevelThousandths - 1_000));
    }
    private static long variedCost(long seed, long day) {
        long entropy = seed ^ (day * 0x9E3779B97F4A7C15L);
        entropy ^= entropy >>> 30; entropy *= 0xbf58476d1ce4e5b9L; entropy ^= entropy >>> 27;
        int percent = 90 + Math.floorMod((int) entropy, 21);
        return BASE_COST * percent / 100;
    }
    private static FrontierAdaptation responseTo(FrontierDamageKind pressure) {
        return switch (pressure) {
            case SCORCH -> FrontierAdaptation.THERMOTOLERANCE;
            case COMBAT, INTERCEPT -> FrontierAdaptation.ARMORED_CARAPACE;
            case CLEANSE -> FrontierAdaptation.CHEMICAL_RESILIENCE;
            case CONTAINMENT -> FrontierAdaptation.BURROWING;
            case QUARANTINE -> FrontierAdaptation.AIRBORNE_PROPAGULES;
            case ILLNESS -> FrontierAdaptation.PARASITIC_MIMICRY;
            case STARVATION -> FrontierAdaptation.RAPID_DIGESTION;
            case SYNAPSE -> FrontierAdaptation.SYNAPTIC_REDUNDANCY;
        };
    }
}
