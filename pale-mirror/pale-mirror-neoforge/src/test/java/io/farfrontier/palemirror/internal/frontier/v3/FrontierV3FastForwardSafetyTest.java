package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3FastForwardSafetyTest {
    @Test
    void unloadedPhysicalIntentDoesNotFreezeColdTimeButLoadedIntentDoes() {
        PhysicalIntent pending = new PhysicalIntent(new PhysicalIntentId("intent:fast-forward-safety"), PhysicalIntentKind.CARGO_HANDOFF,
                PhysicalIntentStatus.PREPARED, new SubjectId("contract:1-2"), List.of(new SubjectId("cargo:1-2")),
                new FixedPosition(FixedScalar.whole(48), FixedScalar.whole(64), FixedScalar.whole(-32)), 0,
                PhysicalPostcondition.CARGO_HANDOFF_OBSERVED);

        assertFalse(FrontierV3FastForwardSafety.requiresPhysicalStep(List.of(pending), List.of(), ignored -> false),
                "a COLD intent in an unloaded chunk has no physical step to skip");
        assertTrue(FrontierV3FastForwardSafety.requiresPhysicalStep(List.of(pending), List.of(), ignored -> true),
                "the same durable intent must stop before an executor could affect a loaded world");
    }
}
