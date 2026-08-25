package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.Map;

/** Source-port launch preconditions and costs; movement is a separate lifecycle. */
final class ReferenceBioformExecutor {
    private static final int MAX_ACTIVE_SPORE_CARRIERS = 1;
    private static final double LAUNCH_POWER_FLOOR = 0.30d;

    private ReferenceBioformExecutor() { }

    static ReferenceSwarm launch(ReferenceInfectionModel model, ReferenceHiveOrgan source, ReferenceBioformKind kind,
                                 int targetX, int targetY, int targetId, Map<ReferenceBioformKind, Double> requested) {
        LinkedHashMap<ReferenceBioformKind, Double> composition = new LinkedHashMap<>();
        Map<ReferenceBioformKind, Double> supplied = requested == null || requested.isEmpty() ? Map.of(kind, 1.0d) : requested;
        for (Map.Entry<ReferenceBioformKind, Double> entry : supplied.entrySet()) if (entry.getValue() > 0.0d) composition.put(entry.getKey(), entry.getValue());
        if (kind == ReferenceBioformKind.HARVESTER || kind == ReferenceBioformKind.SPORE_CARRIER) {
            composition.clear(); composition.put(kind, 1.0d);
        }
        if (composition.isEmpty()) return null;
        if (model.discreteBioforms() && composition.values().stream().anyMatch(value -> value != Math.rint(value))) {
            throw new IllegalArgumentException("individual bioform profiles require whole biological entities");
        }
        if (source.kind() != ReferenceOrganKind.BROOD_SAC && kind != ReferenceBioformKind.SPORE_CARRIER) return null;
        if (source.kind() != ReferenceOrganKind.SPORULATOR && kind == ReferenceBioformKind.SPORE_CARRIER) return null;
        double readiness = model.organReadiness(source);
        if (readiness < minimumReadiness(source.kind())) return null;
        double cost = 0.0d;
        for (Map.Entry<ReferenceBioformKind, Double> entry : composition.entrySet()) cost += biomass(entry.getKey()) * entry.getValue();
        if (source.biomass() < cost || model.swarms.size() >= ReferenceInfectionLimits.MAXIMUM_SWARMS) return null;
        if (kind == ReferenceBioformKind.SPORE_CARRIER && model.swarms.stream().filter(item -> item.kind() == ReferenceBioformKind.SPORE_CARRIER).count() >= MAX_ACTIVE_SPORE_CARRIERS) return null;
        source.biomass(source.biomass() - cost);
        double power = composition.keySet().stream().mapToDouble(ReferenceBioformExecutor::power).max().orElseThrow();
        if (composition.size() > 1) power *= 1.12d;
        double speed = composition.keySet().stream().mapToDouble(ReferenceBioformExecutor::speed).min().orElseThrow();
        ReferenceSwarm swarm = new ReferenceSwarm(model.nextSwarmId(), source.x(), source.y(), power * Math.max(LAUNCH_POWER_FLOOR, readiness)
                / model.combatScale(), targetId, speed, kind, composition, ReferenceFormationPhase.SCREEN, readiness, source.id(), targetX, targetY,
                model.isFeral(source));
        if (kind == ReferenceBioformKind.HARVESTER) { swarm.forageX(targetX); swarm.forageY(targetY); }
        model.swarms.add(swarm);
        return swarm;
    }

    private static double minimumReadiness(ReferenceOrganKind kind) { return kind == ReferenceOrganKind.BROOD_SAC ? 0.42d : kind == ReferenceOrganKind.SPORULATOR ? 0.48d : 0.0d; }
    private static double biomass(ReferenceBioformKind kind) { return switch (kind) { case HARVESTER, RAIDER -> 18.0d; case BREAKER -> 32.0d; case SPORE_CARRIER -> 30.0d; }; }
    private static double power(ReferenceBioformKind kind) { return switch (kind) { case HARVESTER -> 28.0d; case RAIDER -> 370.0d; case BREAKER -> 82.0d; case SPORE_CARRIER -> 14.0d; }; }
    private static double speed(ReferenceBioformKind kind) { return switch (kind) { case HARVESTER -> 0.82d; case RAIDER -> 0.90d; case BREAKER -> 1.50d; case SPORE_CARRIER -> 0.66d; }; }
}
