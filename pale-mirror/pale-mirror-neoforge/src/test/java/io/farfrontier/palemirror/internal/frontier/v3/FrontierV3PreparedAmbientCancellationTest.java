package io.farfrontier.palemirror.internal.frontier.v3;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PreparedAmbientCancellationTest {
    @Test void restartClosureRequiresExactRetainedGenerationRatherThanEmptyAnchor() {
        var actor = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("resident:1-1");
        var prepared = FrontierV3OfflineActorRecoveryPlanTest.prepared();
        var hot = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(prepared, actor,
                io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.HOT);
        var unknown = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(hot, actor,
                io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.UNKNOWN_AFTER_RESTART);
        var lease = unknown.ambientLeases().get(actor);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertFalse(FrontierV3AmbientActorExecutor.restartCustodyIsRetained(unknown, actor, lease, ledger));
        var inactive = FrontierV3AmbientActorExecutor.carrierDeclaration(unknown, actor,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                FrontierV3AmbientActorExecutor.entityId(unknown, actor),
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, lease.revision(), 1L);
        var stale = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertTrue(stale.fence(inactive, lease.revision(), lease.revision() - 1));
        assertFalse(FrontierV3AmbientActorExecutor.restartCustodyIsRetained(unknown, actor, lease, stale));
        assertTrue(ledger.fence(inactive, lease.revision(), lease.revision()));
        assertTrue(FrontierV3AmbientActorExecutor.restartCustodyIsRetained(unknown, actor, lease, ledger));
        assertFalse(FrontierV3AmbientActorExecutor.restartCustodyIsRetained(hot, actor, hot.ambientLeases().get(actor), ledger));
    }
    @Test void retainedInactiveCarrierAllowsCancellationWithoutLoadingItsChunk() {
        assertTrue(FrontierV3AmbientActorExecutor.preparedCancellationHasEvidence(
                FrontierV3AmbientCarrierLedger.Reconciliation.READY, false));
    }

    @Test void firstAdmissionNeedsExplicitUnusedPermitRatherThanARevisionOrEmptyColumn() {
        var absent = FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER;
        assertFalse(FrontierV3AmbientActorExecutor.preparedCancellationHasEvidence(absent, false));
        assertTrue(FrontierV3AmbientActorExecutor.preparedCancellationHasEvidence(absent, true));
    }

    @Test void conflictOrStaleCarrierNeverBecomesPermissionToRelease() {
        for (var carrier : FrontierV3AmbientCarrierLedger.Reconciliation.values()) {
            if (carrier == FrontierV3AmbientCarrierLedger.Reconciliation.READY
                    || carrier == FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER) continue;
            assertFalse(FrontierV3AmbientActorExecutor.preparedCancellationHasEvidence(carrier, true), carrier.name());
        }
    }
}
