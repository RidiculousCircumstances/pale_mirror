package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3FastForwardSafetyTest {
    @Test
    void unloadedPhysicalIntentDoesNotFreezeColdTimeButLoadedIntentDoes() {
        PhysicalIntent pending = new PhysicalIntent(new PhysicalIntentId("intent:fast-forward-safety"), PhysicalIntentKind.CARGO_HANDOFF,
                PhysicalIntentStatus.PREPARED, new SubjectId("contract:1-2"), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.cargoHandoff(new SubjectId("contract:1-2"), new SubjectId("cargo:1-2")),
                new FixedPosition(FixedScalar.whole(48), FixedScalar.whole(64), FixedScalar.whole(-32)), 0,
                PhysicalPostcondition.CARGO_HANDOFF_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.ROUTE_OPERATION);

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
                PhysicalIntentStatus.PREPARED, new SubjectId("site:1-wheat-field"), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.siteHarvest(new SubjectId("site:1-wheat-field"),
                new SubjectId("job:site-harvest-1-wheat-field-1"), new SubjectId("resident:1-1"),
                new SubjectId("custody:field-actor-site-harvest-1-wheat-field-1"), new SubjectId("custody:container-1-depot")),
                new FixedPosition(FixedScalar.whole(48), FixedScalar.whole(64), FixedScalar.whole(-32)), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST);
        assertFalse(FrontierV3FastForwardSafety.requiresPhysicalStep(List.of(preparedHarvest), List.of(), ignored -> true),
                "a prepared harvest has no physical mutation until its named HOT scene makes it RUNNING");
        assertFalse(FrontierV3FastForwardSafety.blockingDescription(List.of(preparedHarvest), List.of(), ignored -> true).contains("prepared-harvest"),
                "a non-executable reservation must not be reported as the blocker of COLD continuation");
    }

    @Test
    void preparedHarvestReservationRemainsColdInTheGenericSafetyPredicate() {
        PhysicalIntent preparedHarvest = new PhysicalIntent(new PhysicalIntentId("intent:prepared-harvest-present"), PhysicalIntentKind.RESOURCE_SITE_HARVEST,
                PhysicalIntentStatus.PREPARED, new SubjectId("site:1-wheat-field"), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.siteHarvest(new SubjectId("site:1-wheat-field"),
                new SubjectId("job:site-harvest-1-wheat-field-1"), new SubjectId("resident:1-1"),
                new SubjectId("custody:field-actor-site-harvest-1-wheat-field-1"), new SubjectId("custody:container-1-depot")),
                new FixedPosition(FixedScalar.whole(48), FixedScalar.whole(64), FixedScalar.whole(-32)), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST);

        assertFalse(FrontierV3FastForwardSafety.requiresPhysicalStep(List.of(preparedHarvest), List.of(), ignored -> true),
                "the generic predicate still distinguishes an unobserved prepared reservation from a running effect");
        assertFalse(FrontierV3FastForwardSafety.blockingDescription(List.of(preparedHarvest), List.of(), ignored -> true)
                        .contains("presentation-demand"),
                "only the live ServerLevel predicate may report an actual ordinary-player presentation boundary");
        assertTrue(FrontierV3FastForwardSafety.preparedHarvestHasPresentObserver(List.of(preparedHarvest), ignored -> true),
                "a live ordinary-player presentation demand is separately visible to the ServerLevel fast-forward boundary");
        assertFalse(FrontierV3FastForwardSafety.preparedHarvestHasPresentObserver(List.of(preparedHarvest), ignored -> false),
                "an unloaded or unobserved prepared harvest remains eligible for COLD continuation");
    }

    @Test
    void releasedHarvestIntentDoesNotRetainPhysicalAuthorityDuringOrdinaryChunkGrace() {
        SubjectId job = new SubjectId("job:site-harvest-1-wheat-field-1");
        SubjectId worker = new SubjectId("resident:1-1");
        PhysicalIntent running = new PhysicalIntent(new PhysicalIntentId("intent:running-harvest"), PhysicalIntentKind.RESOURCE_SITE_HARVEST,
                PhysicalIntentStatus.RUNNING, new SubjectId("site:1-wheat-field"), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.siteHarvest(new SubjectId("site:1-wheat-field"), job, worker,
                new SubjectId("custody:field-actor-site-harvest-1-wheat-field-1"), new SubjectId("custody:container-1-depot")),
                new FixedPosition(FixedScalar.whole(48), FixedScalar.whole(64), FixedScalar.whole(-32)), 0,
                PhysicalPostcondition.RESOURCE_SITE_HARVESTED_OBSERVED,
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.RESOURCE_SITE_HARVEST);
        WorldId world = new WorldId("frontier:test");
        SceneMember member = new SceneMember(worker, SceneLease.deterministicEntityId(world, worker));
        SceneLease active = SceneLease.forCause(new SceneLeaseId("lease:running-harvest"), world,
                new ResourceSiteHarvestSceneCause(new SubjectId("site:1-wheat-field"), job),
                new BlockPosition(48, 64, -32), new SimInstant(7), 9L, SceneLeaseStatus.HOT, List.of(member), java.util.Set.of(), Optional.empty());

        assertTrue(FrontierV3FastForwardSafety.requiresPhysicalStep(List.of(running), List.of(active), ignored -> true),
                "an active exact harvest scene retains the loaded physical boundary");
        assertFalse(FrontierV3FastForwardSafety.requiresPhysicalStep(List.of(running), List.of(), ignored -> true),
                "after fenced scene release a temporarily loaded player chunk is not physical harvest authority");
    }

    @Test
    void unfinishedOwnedProjectionCursorAlsoBlocksColdTime() {
        assertTrue(FrontierV3FastForwardSafety.requiresPhysicalStep(false, true),
                "a durable resource-site cursor may not be skipped after it has written only a prefix");
        assertFalse(FrontierV3FastForwardSafety.requiresPhysicalStep(false, false),
                "an absent cursor does not turn ordinary COLD time into physical work");
        assertEquals("resource-site-projection:site:7-wheat-field",
                FrontierV3FastForwardSafety.blockingDescription("none", "resource-site-projection:site:7-wheat-field"));
    }
}
