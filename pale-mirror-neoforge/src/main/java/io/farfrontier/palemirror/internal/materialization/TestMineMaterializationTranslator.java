package io.farfrontier.palemirror.internal.materialization;

import java.util.List;

import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.WorldObjectId;

/** Deterministically translates a mine's desired domain state into executor operations. */
public final class TestMineMaterializationTranslator {
    public static final String POLICY_ID = "pale_mirror:test_threat";
    public static final String POLICY_VERSION = "2";

    public MaterializationPlan translate(FacilityState facility) {
        WorldObjectId id = facility.id();
        long revision = facility.desiredRevision();
        if (facility.status() == FacilityStatus.INFECTED) {
            return plan(revision, id, MaterializationOperationType.ENSURE_OVERLAY,
                    MaterializationOperationType.ENSURE_TEST_THREAT_CONTROLLER);
        }
        return plan(revision, id, MaterializationOperationType.REMOVE_TEST_THREAT_CONTROLLER,
                MaterializationOperationType.REMOVE_OVERLAY);
    }

    private static MaterializationPlan plan(long revision, WorldObjectId id, MaterializationOperationType first,
                                            MaterializationOperationType second) {
        return new MaterializationPlan(POLICY_ID, POLICY_VERSION, revision, List.of(
                operation(id, revision, 0, first), operation(id, revision, 1, second)));
    }

    private static MaterializationOperation operation(WorldObjectId id, long revision, int index,
                                                       MaterializationOperationType type) {
        String key = id.value() + ":" + revision + ":" + type.name().toLowerCase();
        return new MaterializationOperation("op-" + index, key, type, OperationState.PENDING, 0, "");
    }
}
