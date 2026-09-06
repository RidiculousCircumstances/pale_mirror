package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Source-exact non-volitional hive state prepared before its planner observes a world. */
final class ReferenceHivePreparation {
    private static final int ADAPTATION_INTERVAL_DAYS = 10;
    private static final double ADAPTATION_COST = 12.0d;
    private static final double ADAPTATION_JITTER = 0.10d;

    private ReferenceHivePreparation() { }

    static void prepare(ReferenceInfectionModel model, int day) {
        model.refreshFeralStatus();
        maybeAdapt(model, day);
    }

    private static void maybeAdapt(ReferenceInfectionModel model, int day) {
        if (day % ADAPTATION_INTERVAL_DAYS != 0) return;
        double samples = 0.0d;
        for (ReferenceHiveOrgan organ : model.organs.values()) samples += organ.samples();
        if (samples < ADAPTATION_COST) return;
        String response = dominantDamage(model.damageMemory);
        String adaptation = adaptationFor(response);
        double cost = ADAPTATION_COST * model.rng.uniform(1.0d - ADAPTATION_JITTER, 1.0d + ADAPTATION_JITTER);
        List<ReferenceHiveOrgan> donors = new ArrayList<>(model.organs.values());
        donors.sort(Comparator.comparingDouble((ReferenceHiveOrgan item) -> item.samples()).reversed());
        double remaining = cost;
        for (ReferenceHiveOrgan organ : donors) {
            double paid = Math.min(organ.samples(), remaining);
            organ.samples(organ.samples() - paid);
            remaining -= paid;
            if (remaining <= 0.0d) break;
        }
        if (remaining <= 0.0d) {
            model.genomeLevel(adaptation, Math.min(2.0d, model.genome().getOrDefault(adaptation, 0.0d) + 1.0d));
            model.projectHistory.add(new ReferenceHiveHistoryEvent(day, "adaptation:" + adaptation,
                    donors.isEmpty() ? -1 : donors.getFirst().id(), null, -1, -1));
        }
    }

    private static String dominantDamage(Map<String, Double> damage) {
        String result = "starvation";
        double highest = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> item : damage.entrySet()) {
            if (item.getValue() > highest) {
                result = item.getKey();
                highest = item.getValue();
            }
        }
        return result;
    }

    private static String adaptationFor(String damage) {
        return switch (damage) {
            case "scorch" -> "thermotolerance";
            case "combat", "intercept" -> "armored_carapace";
            case "cleanse" -> "chemical_resilience";
            case "containment" -> "burrowing";
            case "quarantine" -> "airborne_spores";
            case "illness" -> "parasitic_mimicry";
            case "starvation" -> "rapid_digestion";
            case "synapse" -> "synaptic_redundancy";
            default -> throw new IllegalArgumentException("unknown source hive damage response: " + damage);
        };
    }
}
