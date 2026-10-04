package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.LinkedHashMap;

/** Composition of declared domain relationships, never discovery of a physical body owner. */
public final class FrontierActorDeathConsequences implements ActorDeathConsequences {
    public static final FrontierActorDeathConsequences INSTANCE = new FrontierActorDeathConsequences();
    private FrontierActorDeathConsequences() { }

    @Override public Settlement settle(FrontierWorldState state, SubjectId actor, long atTick) {
        var leases = new LinkedHashMap<>(state.sceneLeases());
        SceneLease participantScope = null;
        var stoppedPlans = state.strategicPlans();
        for (var scene : state.sceneLeases().values()) {
            if (!scene.retainsMemberCustody(actor)) continue;
            // The aggregate currently guarantees one active process scope per participant.
            // Do not silently choose the first entry if that relationship is ever corrupted.
            if (participantScope != null) throw new IllegalArgumentException("death has multiple active participant scopes");
            participantScope = scene;
            // Stage quiescence, but do not build a half-transition aggregate. Family
            // consequences and the physical death commit with it against this same basis.
            var next = switch (scene.status()) {
                case HOT -> SceneLeaseStatus.DRAINING;
                case PREPARED -> SceneLeaseStatus.CONFLICT;
                default -> scene.status();
            };
            if (next != scene.status()) {
                var stopped = scene.withStatus(next);
                leases.put(scene.id(), stopped);
                stoppedPlans = FrontierSceneBehaviors.transitionPlans(state, scene, next);
            }
        }
        var changes = FrontierWorldStateUpdate.begin();
        if (stoppedPlans != state.strategicPlans()) changes.strategicPlans(stoppedPlans);
        var activities = ActorExecutionComposition.LIFECYCLE.acknowledgeActivityDeath(state, actor, state.actorExecutions(), atTick);
        changes.merge(activities.changes());
        var ambient = new LinkedHashMap<>(state.ambientLeases());
        var presentation = ambient.get(actor);
        if (presentation != null && presentation.status() != AmbientLeaseStatus.CLOSED)
            ambient.put(actor, presentation.withStatus(AmbientLeaseStatus.CLOSED));
        return new Settlement(state, changes.ambientLeases(ambient).sceneLeases(leases)
                .companies(state.companies().acknowledgeDeath(actor)), activities.executions());
    }
}
