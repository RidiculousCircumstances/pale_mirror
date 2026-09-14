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
        assertTrue(FrontierV3FastForwardSafety.blockingDescription(List.of(pending), List.of(), ignored -> true)
                        .contains("intent:fast-forward-safety:CARGO_HANDOFF:PREPARED"),
                "a stopped COLD interval must expose the exact loaded intent rather than only a generic busy state");
    }

    @Test
    void preparedHarvestReservationDoesNotMasqueradeAsAnExecutableColdBoundary() {
        PhysicalIntent preparedHarvest = new PhysicalIntent(new PhysicalIntentId("intent:prepared-harvest"), PhysicalIntentKind.RESOURCE_SITE_HARVEST,
                PhysicalIntentStatus.PREPARED, new SubjectId("site:1-wheat-field"), List.of(new SubjectId("site:1-wheat-field"),
                new SubjectId("job:site-harvest-1-wheat-field-1"), new SubjectId("resident:1-1"), new SubjectId("item:site-harvest-1-wheat-field-1-wheat")),
                new FixedPosition(FixedScalar.whole(48), FixedScalar.whole(64), FixedScalar.whole(-32)), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED);
        assertFalse(FrontierV3FastForwardSafety.requiresPhysicalStep(List.of(preparedHarvest), List.of(), ignored -> true),
                "a prepared harvest has no physical mutation until its named HOT scene makes it RUNNING");
        assertFalse(FrontierV3FastForwardSafety.blockingDescription(List.of(preparedHarvest), List.of(), ignored -> true).contains("prepared-harvest"),
                "a non-executable reservation must not be reported as the blocker of COLD continuation");
    }
}
