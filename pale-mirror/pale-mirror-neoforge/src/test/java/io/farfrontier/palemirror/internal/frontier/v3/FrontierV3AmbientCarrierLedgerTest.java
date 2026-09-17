package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3AmbientCarrierLedgerTest {
    private static final SubjectId ACTOR = new SubjectId("resident:7-31");
    private static final UUID UUID_A = UUID.fromString("ef562345-8f47-37ec-af28-d12c259ab948");

    @Test
    void fencedCarrierPermitsOnlyOneNewerSameUuidAdoption() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();

        assertTrue(ledger.canFence(ACTOR, UUID_A, 7L, 7L, 3L));
        assertTrue(ledger.fence(ACTOR, UUID_A, 7L, 7L, 3L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.READY, ledger.reconciliation(ACTOR, UUID_A, 8L));
        assertEquals(4L, ledger.reconstructionEpoch(ACTOR));
        assertTrue(ledger.adopt(ACTOR, UUID_A, 8L));
        assertEquals(0, ledger.inactiveCount(), "adoption leaves no concurrent inactive custody");
    }

    @Test
    void missingOrInvalidCarrierNeverSelectsAReplacement() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        UUID foreign = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER, ledger.reconciliation(ACTOR, UUID_A, 8L));
        assertTrue(ledger.fence(ACTOR, UUID_A, 7L, 7L, 1L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.UUID_MISMATCH, ledger.reconciliation(ACTOR, foreign, 8L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.STALE_REVISION, ledger.reconciliation(ACTOR, UUID_A, 7L));
        assertFalse(ledger.adopt(ACTOR, foreign, 8L));
        assertFalse(ledger.adopt(ACTOR, UUID_A, 7L));
        assertEquals(1, ledger.inactiveCount(), "every ambiguous path retains the original exact carrier");
    }

    @Test
    void sceneCarrierUsesPhysicalRevisionWithoutFalselyRejectingFirstAmbientReturn() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();

        assertTrue(ledger.fence(ACTOR, UUID_A, 2_538L, 0L, 4L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.READY, ledger.reconciliation(ACTOR, UUID_A, 1L));
        assertEquals(5L, ledger.reconstructionEpoch(ACTOR));
        assertTrue(ledger.adopt(ACTOR, UUID_A, 1L));
    }

    @Test
    void fencedCarrierPermitsOnlyOneNewerSameUuidSceneReturn() {
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        UUID foreign = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertTrue(ledger.fence(ACTOR, UUID_A, 2_538L, 0L, 4L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.READY, ledger.sceneReconciliation(ACTOR, UUID_A, 7_353L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.UUID_MISMATCH, ledger.sceneReconciliation(ACTOR, foreign, 7_353L));
        assertEquals(FrontierV3AmbientCarrierLedger.Reconciliation.STALE_REVISION, ledger.sceneReconciliation(ACTOR, UUID_A, 2_538L));
        assertEquals(5L, ledger.reconstructionEpoch(ACTOR));
        assertTrue(ledger.adoptScene(ACTOR, UUID_A, 7_353L));
        assertEquals(0, ledger.inactiveCount(), "scene return consumes the sole inactive carrier");
    }

    @Test
    void inspectionAddsOnlyFencedCarrierEvidenceToTheCanonicalRelationshipView() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:carrier-inspection"), 91L));
        FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.emptyForTest();

        assertTrue(ledger.fence(ACTOR, UUID_A, 7L, 3L, 2L));
        FrontierDomainRelationships.View canonical = FrontierDomainRelationships.view(state, 19L);
        FrontierDomainRelationships.View inspected = FrontierV3RelationshipInspection.inspect(state, 19L, ledger);
        FrontierDomainRelationships.CarrierEvidenceEndpoint carrier = new FrontierDomainRelationships.CarrierEvidenceEndpoint(ACTOR, "carrier:" + UUID_A);

        assertEquals(canonical.edges().size() + 1, inspected.edges().size());
        assertEquals(19L, inspected.canonicalRevision());
        assertTrue(inspected.causalChain(carrier).stream().anyMatch(edge -> edge.kind() == FrontierDomainRelationships.Kind.ACTOR_CARRIER_EVIDENCE));
        assertEquals(canonical, FrontierDomainRelationships.view(state, 19L), "inspection cannot mutate canonical relationships");
    }
}
