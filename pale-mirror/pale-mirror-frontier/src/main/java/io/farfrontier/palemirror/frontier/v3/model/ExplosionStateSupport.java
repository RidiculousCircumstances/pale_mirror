package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure validation and terminal state assembly for one fully inspected physical blast. */
public final class ExplosionStateSupport {
    private ExplosionStateSupport() { }

    static void validateReceipt(PhysicalIntent intent, ExplosionObservation observation) {
        if (intent.kind() != PhysicalIntentKind.EXPLOSION || !intent.origin().equals(observation.origin())
                || intent.radiusBlocks() != observation.radiusBlocks()) {
            throw new IllegalArgumentException("explosion observation differs from its confirmed intent");
        }
    }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.status() != PhysicalIntentStatus.PREPARED) {
            throw new IllegalArgumentException("explosion intent has invalid physical lifecycle");
        }
        validateRetainedIntent(state, intent);
    }

    /** Recovery validates the same producer-stamped effect identity without treating RUNNING as a new prepare. */
    public static void validateRetainedIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXPLOSION || intent.lifecycleOwner() != PhysicalIntentLifecycleOwner.HIVE_MOBILIZATION
                || (intent.status() != PhysicalIntentStatus.PREPARED && intent.status() != PhysicalIntentStatus.RUNNING
                && intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)) {
            throw new IllegalArgumentException("explosion intent has invalid retained physical lifecycle");
        }
        Bioform bomber = java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(value -> value.id().equals(intent.causeSubjectId())).findFirst().orElseThrow(() -> new IllegalArgumentException("explosion cause is not one hive bioform"));
        if (!bomber.isExplosiveAssaulter() || state.actorLocations().get(bomber.id()).condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("explosion cause must be one living bomber bioform");
        }
        SceneLease lease = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).filter(value -> value.status() == SceneLeaseStatus.HOT)
                .filter(value -> FrontierSceneBehaviors.logistics(value).engagementId().isPresent()).filter(value -> value.members().stream().anyMatch(member -> member.actorId().equals(bomber.id())))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("explosion requires one HOT bomber scene"));
        RouteEngagement engagement = state.strategicPlans().routeEngagements().get(FrontierSceneBehaviors.logistics(lease).engagementId().orElseThrow());
        if (engagement == null || engagement.status() != RouteEngagementStatus.HOT || !engagement.attackerIds().contains(bomber.id())
                || !intent.roles().equals(PhysicalIntentRoleBinding.explosion(bomber.id(), engagement.id()))) {
            throw new IllegalArgumentException("explosion must bind its exact HOT bomber and engagement");
        }
    }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, ExplosionObservation observation,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        validateReceipt(intent, observation);
        intents.put(intent.id(), intent.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observation.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(observation.id(), observation);
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).physicalObservations(observations));
    }
}
