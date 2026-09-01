package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3TraversalFoundryAuditTest {
    @Test void compiledTraversalPlanProvesTypedPortsPublicConnectivityAndNonFlatSupport() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:foundry-traversal"), 1_072L));

        var report = FrontierV3TraversalFoundryAudit.auditCompiled(state);

        assertTrue(report.passed(), report::summary);
        assertEquals(48D, metric(report, "frontier.traversal.topologies"));
        assertEquals(24D, metric(report, "frontier.port.count"));
        assertTrue(metric(report, "frontier.traversal.edges") > 0D);
        assertEquals(0D, metric(report, "frontier.traversal.missing_support"));
        assertEquals(0D, metric(report, "frontier.port.disconnected"));
    }

    @Test void compiledSurveyedRampHasOwnedFootingsWithoutPlayerScaffolding() {
        FrontierWorldState state = FrontierV3FixtureCatalog.steppedRouteConfiguration(new WorldId("frontier:foundry-surveyed-ramp"), 41L).initialState();

        var report = FrontierV3TraversalFoundryAudit.auditCompiled(state);

        assertTrue(report.passed(), report::summary);
        assertEquals(0D, metric(report, "frontier.traversal.missing_support"));
        assertTrue(FrontierGrayboxPlan.compile(state).cells().values().stream()
                .anyMatch(cell -> cell.semanticPart() == GrayboxSemanticPart.ROUTE_FOUNDATION),
                "the compiled Foundry plan owns the raised route footing rather than awaiting a player block");
    }

    @Test void runtimeSupportPendingIsOnlyFreshAirWithoutAnyOwnershipClaim() {
        GrayboxCell route = new GrayboxCell(new BlockPosition(8, 64, 8), new SubjectId("route:test"),
                GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE);
        FrontierV3GrayboxLedger.Claim claimed = new FrontierV3GrayboxLedger.Claim("route:test", "ROUTE", "ROUTE_SURFACE", false);
        FrontierV3GrayboxLedger.Claim conflicted = new FrontierV3GrayboxLedger.Claim("route:test", "ROUTE", "ROUTE_SURFACE", true);

        assertEquals(FrontierV3TraversalFoundryAudit.RuntimeSupportStatus.PENDING,
                FrontierV3TraversalFoundryAudit.classifyRuntimeSupport(route, null, FrontierV3TraversalFoundryAudit.ObservedSupport.AIR),
                "only fresh air without a claim is an ordinary loaded-chunk projection delay");
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimeSupportStatus.MISMATCH,
                FrontierV3TraversalFoundryAudit.classifyRuntimeSupport(route, null, FrontierV3TraversalFoundryAudit.ObservedSupport.EXPECTED),
                "an unclaimed matching-looking player/world block must never be adopted as materialized truth");
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimeSupportStatus.MISMATCH,
                FrontierV3TraversalFoundryAudit.classifyRuntimeSupport(route, claimed, FrontierV3TraversalFoundryAudit.ObservedSupport.OTHER),
                "a changed owned support remains visible drift rather than pending work");
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimeSupportStatus.MISMATCH,
                FrontierV3TraversalFoundryAudit.classifyRuntimeSupport(route, conflicted, FrontierV3TraversalFoundryAudit.ObservedSupport.EXPECTED),
                "a previously conflicted claim remains a conflict even if a later block happens to look right");
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimeSupportStatus.CURRENT,
                FrontierV3TraversalFoundryAudit.classifyRuntimeSupport(route, claimed, FrontierV3TraversalFoundryAudit.ObservedSupport.EXPECTED));
    }

    @Test void runtimePortAvailabilityNeedsBothDeclaredHeadroomCellsAndNeverTurnsAWorldChangeIntoAnAlternateEntrance() {
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimePortAvailability.OPEN,
                FrontierV3TraversalFoundryAudit.classifyPortAvailability(List.of(
                        FrontierV3TraversalFoundryAudit.ObservedPortHeadroom.CLEAR,
                        FrontierV3TraversalFoundryAudit.ObservedPortHeadroom.CLEAR)));
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimePortAvailability.BLOCKED,
                FrontierV3TraversalFoundryAudit.classifyPortAvailability(List.of(
                        FrontierV3TraversalFoundryAudit.ObservedPortHeadroom.BLOCKED,
                        FrontierV3TraversalFoundryAudit.ObservedPortHeadroom.CLEAR)),
                "one occupied body cell blocks the one declared semantic port rather than permitting a hidden sidestep");
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimePortAvailability.UNVERIFIED,
                FrontierV3TraversalFoundryAudit.classifyPortAvailability(List.of(
                        FrontierV3TraversalFoundryAudit.ObservedPortHeadroom.CLEAR,
                        FrontierV3TraversalFoundryAudit.ObservedPortHeadroom.UNVERIFIED)));
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TraversalFoundryAudit.classifyPortAvailability(List.of(
                FrontierV3TraversalFoundryAudit.ObservedPortHeadroom.CLEAR)));
    }

    @Test void runtimeEdgeAvailabilityIsBlockedByObservedDriftButNotByUnmaterializedOrUnloadedProviderWork() {
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimeEdgeAvailability.OPEN,
                FrontierV3TraversalFoundryAudit.classifyEdgeAvailability(
                        FrontierV3TraversalFoundryAudit.RuntimeSurfaceStatus.CURRENT,
                        FrontierV3TraversalFoundryAudit.RuntimeSurfaceStatus.CURRENT));
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimeEdgeAvailability.PENDING,
                FrontierV3TraversalFoundryAudit.classifyEdgeAvailability(
                        FrontierV3TraversalFoundryAudit.RuntimeSurfaceStatus.CURRENT,
                        FrontierV3TraversalFoundryAudit.RuntimeSurfaceStatus.PENDING));
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimeEdgeAvailability.UNVERIFIED,
                FrontierV3TraversalFoundryAudit.classifyEdgeAvailability(
                        FrontierV3TraversalFoundryAudit.RuntimeSurfaceStatus.CURRENT,
                        FrontierV3TraversalFoundryAudit.RuntimeSurfaceStatus.UNVERIFIED));
        assertEquals(FrontierV3TraversalFoundryAudit.RuntimeEdgeAvailability.BLOCKED,
                FrontierV3TraversalFoundryAudit.classifyEdgeAvailability(
                        FrontierV3TraversalFoundryAudit.RuntimeSurfaceStatus.MISMATCH,
                        FrontierV3TraversalFoundryAudit.RuntimeSurfaceStatus.CURRENT),
                "one conflicting owned support makes only its existing edge unavailable; it cannot authorize a detour");
    }

    private static double metric(io.farfrontier.palemirror.api.FoundryAuditReport report, String id) {
        return report.metrics().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow().value();
    }
}
