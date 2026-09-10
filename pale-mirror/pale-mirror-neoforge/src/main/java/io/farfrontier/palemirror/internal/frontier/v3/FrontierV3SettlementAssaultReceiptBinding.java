package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation;

import java.util.Objects;

/** Exact, non-causal identity binding for one physical settlement-assault receipt. */
final class FrontierV3SettlementAssaultReceiptBinding {
    private FrontierV3SettlementAssaultReceiptBinding() { }

    static PhysicalIntentId intentId(FrontierWorldState state, SceneLease lease, SubjectId cause) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(lease, "lease"); Objects.requireNonNull(cause, "cause");
        if (!FrontierSceneBehaviors.isSettlementAssault(lease)) {
            throw new IllegalArgumentException("only a settlement-assault lease has this receipt binding");
        }
        String world = state.bootstrap().worldId().value().replace(':', '-');
        String key = world + "-" + cause.value().replace(':', '-') + "-r" + lease.revision() + "-s0";
        return new PhysicalIntentId("intent:scene-strike-" + key);
    }

    static boolean belongsToLease(FrontierWorldState state, SceneLease lease, PhysicalIntent intent) {
        return intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.id().equals(intentId(state, lease, intent.causeSubjectId()));
    }

    /**
     * Keeps the test oracle independent from the strike transition: it compares the two real
     * entity samples with the retained immutable receipt rather than trusting a decreasing value.
     */
    static void requireObservedHealthTransition(PhysicalIntent intent, SceneStrikeObservation receipt,
                                                FixedScalar targetHealthBefore, FixedScalar targetHealthAfter) {
        Objects.requireNonNull(intent, "intent"); Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(targetHealthBefore, "target health before"); Objects.requireNonNull(targetHealthAfter, "target health after");
        if (!receipt.intentId().equals(intent.id()) || !receipt.attackerId().equals(intent.subjectIds().getFirst())
                || !receipt.targetId().equals(intent.subjectIds().getLast())) {
            throw new IllegalArgumentException("scene strike receipt does not bind its exact intent members");
        }
        if (!receipt.targetHealthBefore().equals(targetHealthBefore) || !receipt.targetHealthAfter().equals(targetHealthAfter)
                || targetHealthAfter.compareTo(targetHealthBefore) >= 0) {
            throw new IllegalArgumentException("scene strike receipt does not match independently observed Minecraft health");
        }
    }
}
