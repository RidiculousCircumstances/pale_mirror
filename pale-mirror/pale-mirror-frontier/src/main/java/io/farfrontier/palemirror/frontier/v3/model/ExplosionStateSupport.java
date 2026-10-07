package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
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
        SubjectId assaultId = intent.roles().require(PhysicalIntentSubjectRole.ENGAGEMENT);
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(assaultId);
        boolean hot = state.sceneLeases().values().stream()
                .filter(FrontierSceneBehaviors::isSettlementAssault)
                .anyMatch(lease -> lease.status() == SceneLeaseStatus.HOT
                        && FrontierSceneBehaviors.settlementAssault(lease).assaultId().equals(assaultId)
                        && lease.members().stream().anyMatch(member -> member.actorId().equals(bomber.id())));
        if (assault == null || assault.status() != SettlementAssaultStatus.HOT || !hot
                || !assault.attackerIds().contains(bomber.id())
                || !intent.roles().equals(PhysicalIntentRoleBinding.explosion(bomber.id(), assaultId))) {
            throw new IllegalArgumentException("explosion must bind its exact HOT bomber and settlement assault");
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
