package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** One exact fence for a physical intent; the intent ID is its immutable owner-version boundary. */
public final class FencedRecoveryPhysicalIntentSupport {
    private FencedRecoveryPhysicalIntentSupport() { }

    public static FencedRecoveryState prepared(FencedRecoveryState recovery, PhysicalIntent intent) {
        SubjectId id = bindingId(intent); long epoch = recovery.nextEpoch(id);
        return recovery.prepare(FencedRecoveryBinding.prepared(id, asset(intent), intent.causeSubjectId(), 0L, epoch, false));
    }
    public static FencedRecoveryState transition(FencedRecoveryState recovery, PhysicalIntent intent, PhysicalIntentStatus status) {
        SubjectId id = bindingId(intent); FencedRecoveryBinding binding = recovery.current().get(id);
        if (binding == null || binding.asset() != asset(intent) || !binding.ownerId().equals(intent.causeSubjectId())) {
            throw new IllegalArgumentException("physical intent recovery authority is absent or stale");
        }
        long epoch = binding.authorityEpoch();
        return switch (status) {
            case RUNNING -> recovery.running(id, epoch);
            case CONFIRMED -> recovery.observed(id, epoch).confirm(id, epoch);
            case UNKNOWN_AFTER_RESTART -> recovery.ambiguous(id, epoch, "intent-restart-uninspected", FencedRecoveryDisposition.INSPECT);
            case PREPARED -> throw new IllegalArgumentException("physical intent cannot transition back to prepared");
        };
    }
    public static SubjectId bindingId(PhysicalIntent intent) {
        return new SubjectId("recovery:intent_" + intent.id().value().replace(':', '_'));
    }
    private static FencedRecoveryAsset asset(PhysicalIntent intent) {
        return switch (intent.kind()) {
            case CARGO_HANDOFF, CARGO_LOADING, ROUTE_CONSTRUCTION_MATERIAL_LOADING, ROUTE_MAINTENANCE_MATERIAL_LOADING,
                    HIVE_NUTRIENT_DEPARTURE, HIVE_NUTRIENT_ARRIVAL -> FencedRecoveryAsset.CARGO;
            case EQUIPMENT_RETURN -> FencedRecoveryAsset.CONTAINER;
            default -> FencedRecoveryAsset.EFFECT;
        };
    }
}
