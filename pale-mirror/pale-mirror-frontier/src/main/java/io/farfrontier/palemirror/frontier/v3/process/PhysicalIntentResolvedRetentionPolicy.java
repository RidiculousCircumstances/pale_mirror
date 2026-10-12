package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhysicalIntentSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** Owner-supplied proof that one resolved physical intent may leave active lifecycle retention. */
@FunctionalInterface
interface PhysicalIntentResolvedRetentionPolicy {
    boolean mayCompact(FrontierWorldState state, PhysicalIntent intent);

    /**
     * A family explicitly choosing this policy may retire only a settled receipt which carries
     * no current recovery authority. UNKNOWN/CONFLICTED and a forged confirmed/current pair
     * therefore remain retained for recovery rather than being converted into history.
     */
    static PhysicalIntentResolvedRetentionPolicy confirmedReceiptWithoutRecovery() {
        return (state, intent) -> intent.status() == PhysicalIntentStatus.CONFIRMED
                && !state.fencedRecovery().current().containsKey(FencedRecoveryPhysicalIntentSupport.bindingId(intent));
    }

    static PhysicalIntentResolvedRetentionPolicy none() { return (state, intent) -> false; }

    /** Explicit opt-in to a typed common proof; a generic conflict or diagnostic string is not cancellation. */
    static PhysicalIntentResolvedRetentionPolicy cancelledBeforeEffect() {
        return (state, intent) -> {
            var id = FencedRecoveryPhysicalIntentSupport.bindingId(intent);
            var tombstone = state.fencedRecovery().tombstones().get(id);
            return intent.status() == PhysicalIntentStatus.CONFLICTED && intent.postconditionObservationId().isEmpty()
                    && !state.fencedRecovery().current().containsKey(id) && tombstone != null
                    && tombstone.ownerId().equals(intent.causeSubjectId())
                    && tombstone.disposition() == io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryDisposition.CANCELLED_BEFORE_EFFECT;
        };
    }
}
