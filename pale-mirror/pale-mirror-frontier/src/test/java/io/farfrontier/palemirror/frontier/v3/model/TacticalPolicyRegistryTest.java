package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TacticalPolicyRegistryTest {
    @Test
    void durablePlanRejectsMissingDuplicateAndIncompatiblePolicyOwnership() {
        assertDoesNotThrow(() -> plan(TacticalPolicyRegistry.ROUTE_PATROL,
                List.of(TacticalBehaviour.HOLD_FORMATION, TacticalBehaviour.ADVANCE_CHECKPOINT)));
        assertThrows(IllegalArgumentException.class, () -> plan(new TacticalPolicyDescriptor("frontier:foreign-policy", 1),
                List.of(TacticalBehaviour.HOLD_FORMATION)), "an unregistered tactical policy must fail closed");
        assertThrows(IllegalArgumentException.class, () -> plan(TacticalPolicyRegistry.ROUTE_PATROL, List.of()),
                "a plan without individual behaviour authority must fail closed");
        assertThrows(IllegalArgumentException.class, () -> plan(TacticalPolicyRegistry.ROUTE_PATROL,
                List.of(TacticalBehaviour.HOLD_FORMATION, TacticalBehaviour.HOLD_FORMATION)),
                "one individual behaviour may not be duplicated under a tactical owner");
        assertThrows(IllegalArgumentException.class, () -> plan(TacticalPolicyRegistry.ROUTE_PATROL,
                List.of(TacticalBehaviour.ENGAGE_WITHIN_ENVELOPE)),
                "an individual behaviour owned by hive expedition must not silently transfer to patrol");
    }

    private static TacticalPlan plan(TacticalPolicyDescriptor policy, List<TacticalBehaviour> behaviours) {
        SubjectId actor = new SubjectId("resident:policy");
        return new TacticalPlan(new SubjectId("plan:policy"), new SubjectId("operation:policy"), new SubjectId("settlement:1"), 0L, 0L,
                policy, TacticalPlanPhase.ASSEMBLE, List.of(new SubjectId("objective:policy")), Map.of(actor, TacticalRole.LEADER), behaviours,
                new BlockPosition(0, 64, 0), new BlockPosition(1, 64, 0), new BlockPosition(0, 64, 0));
    }
}
