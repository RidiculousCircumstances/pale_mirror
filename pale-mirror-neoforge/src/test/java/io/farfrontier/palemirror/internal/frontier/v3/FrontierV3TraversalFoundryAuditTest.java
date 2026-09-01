package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static double metric(io.farfrontier.palemirror.api.FoundryAuditReport report, String id) {
        return report.metrics().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow().value();
    }
}
