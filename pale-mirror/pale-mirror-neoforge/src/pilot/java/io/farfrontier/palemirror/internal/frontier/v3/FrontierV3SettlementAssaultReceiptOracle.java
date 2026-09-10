package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation;

import java.util.Objects;

/** Test-only independent samples for the local production-strike component fixture. */
final class FrontierV3SettlementAssaultReceiptOracle {
    private FrontierV3SettlementAssaultReceiptOracle() { }

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
