package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.Map;

/** Source-port compatibility entry points retained by Python's InfectionModel. */
final class ReferenceInfectionLegacy {
    private static final double MUTATION_SAMPLES_COST = 8.0d;
    private static final double SPORE_PROJECT_COST = 5.0d;
    private static final double SPORE_PROJECT_STRENGTH = 0.18d;
    private static final double ARMOURED_TARGET_DEFENCE = 130.0d;

    private ReferenceInfectionLegacy() { }

    static ReferenceGridPosition nestGrowthTarget(ReferenceInfectionModel model, ReferenceHiveOrgan nest) {
        double score = Double.NEGATIVE_INFINITY;
        ReferenceGridPosition result = null;
        for (int y = 0; y < model.height; y++) for (int x = 0; x < model.width; x++) {
            double candidate = model.ecosystem.cell(x, y).organicMass() * model.level[y][x];
            if (result == null || candidate > score || candidate == score && (x > result.x() || x == result.x() && y > result.y())) {
                result = new ReferenceGridPosition(x, y);
                score = candidate;
            }
        }
        return result;
    }

    static ReferenceMutation chooseMutation(ReferenceHiveOrgan nest) {
        ReferenceMutation result = null;
        int level = Integer.MAX_VALUE;
        for (ReferenceMutation mutation : ReferenceMutation.values()) {
            int candidate = nest.mutationLevel(mutation);
            if (candidate < level) {
                result = mutation;
                level = candidate;
            }
        }
        return result;
    }

    static boolean launchMutation(ReferenceInfectionModel model, ReferenceHiveOrgan nest, ReferenceMutation mutation, int day) {
        if (nest.samples() < MUTATION_SAMPLES_COST) return false;
        nest.samples(nest.samples() - MUTATION_SAMPLES_COST);
        nest.mutation(mutation, nest.mutationLevel(mutation) + 1);
        model.projectHistory.add(new ReferenceHiveHistoryEvent(day, "legacy_mutation:" + mutation.name().toLowerCase(), nest.id(), null, nest.x(), nest.y()));
        return true;
    }

    static boolean launchLocalPropagation(ReferenceInfectionModel model, ReferenceHiveOrgan nest, int day) {
        if (nest.biomass() < SPORE_PROJECT_COST) return false;
        nest.biomass(nest.biomass() - SPORE_PROJECT_COST);
        for (int yy = Math.max(0, nest.y() - 2); yy < Math.min(model.height, nest.y() + 3); yy++) {
            for (int xx = Math.max(0, nest.x() - 2); xx < Math.min(model.width, nest.x() + 3); xx++) {
                model.level[yy][xx] = Math.min(1.0d, model.level[yy][xx] + SPORE_PROJECT_STRENGTH / (1.0d + Math.hypot(xx - nest.x(), yy - nest.y())));
            }
        }
        nest.lastProjectDay(day);
        return true;
    }

    static ReferenceSwarm launchSwarm(ReferenceInfectionModel model, ReferenceHiveOrgan nest,
                                       Map<Integer, ReferenceSettlement> settlements, int targetId, int day) {
        ReferenceSettlement target = settlements.get(targetId);
        if (target == null) return null;
        Map<ReferenceBioformKind, Double> composition = new LinkedHashMap<>();
        if (target.defenceStrength() > ARMOURED_TARGET_DEFENCE) {
            composition.put(ReferenceBioformKind.RAIDER, 1.0d);
            composition.put(ReferenceBioformKind.BREAKER, 1.0d);
        } else {
            composition.put(ReferenceBioformKind.RAIDER, 1.0d);
        }
        ReferenceSwarm result = model.launchBioform(nest, ReferenceBioformKind.RAIDER, target.x(), target.y(), targetId, composition);
        if (result != null) nest.lastProjectDay(day);
        return result;
    }
}
