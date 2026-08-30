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

    static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXPLOSION || intent.status() != PhysicalIntentStatus.PREPARED) {
            throw new IllegalArgumentException("explosion intent has invalid physical lifecycle");
        }
        Bioform bomber = java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(value -> value.id().equals(intent.causeSubjectId())).findFirst().orElseThrow(() -> new IllegalArgumentException("explosion cause is not one hive bioform"));
        if (bomber.role() != BioformRole.BOMBER || state.actorLocations().get(bomber.id()).condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("explosion cause must be one living bomber bioform");
        }
        SceneLease lease = state.sceneLeases().values().stream().filter(value -> value.status() == SceneLeaseStatus.HOT)
                .filter(value -> value.engagementId().isPresent()).filter(value -> value.members().stream().anyMatch(member -> member.actorId().equals(bomber.id())))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("explosion requires one HOT bomber scene"));
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(lease.engagementId().orElseThrow());
        if (engagement == null || engagement.status() != RouteEngagementStatus.HOT || !engagement.attackerIds().contains(bomber.id())
                || intent.subjectIds().size() != 2 || !intent.subjectIds().getFirst().equals(bomber.id()) || !intent.subjectIds().getLast().equals(engagement.id())) {
            throw new IllegalArgumentException("explosion must bind its exact HOT bomber and engagement");
        }
    }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, ExplosionObservation observation,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        validateReceipt(intent, observation);
        intents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observation.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(observation.id(), observation);
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(),
                state.productionJobs(), state.contracts(), state.operations(), state.logisticsHistory(), intents, observations, state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans(), state.humanPopulation(), state.companies(), state.resourceSites());
    }
}
