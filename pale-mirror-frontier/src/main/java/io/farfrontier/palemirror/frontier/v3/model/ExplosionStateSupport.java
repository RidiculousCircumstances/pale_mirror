package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure validation and terminal state assembly for one fully inspected physical blast. */
final class ExplosionStateSupport {
    private ExplosionStateSupport() { }

    static void validateReceipt(PhysicalIntent intent, ExplosionObservation observation) {
        if (intent.kind() != PhysicalIntentKind.EXPLOSION || !intent.origin().equals(observation.origin())
                || intent.radiusBlocks() != observation.radiusBlocks()) {
            throw new IllegalArgumentException("explosion observation differs from its confirmed intent");
        }
    }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, ExplosionObservation observation,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        validateReceipt(intent, observation);
        intents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observation.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(observation.id(), observation);
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(),
                state.productionJobs(), state.contracts(), state.operations(), intents, observations, state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans());
    }
}
