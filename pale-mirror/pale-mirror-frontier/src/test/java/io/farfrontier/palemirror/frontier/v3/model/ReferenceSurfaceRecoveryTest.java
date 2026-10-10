package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceSurfaceRecoveryTest {
    private static final SubjectId DEPOT = new SubjectId("container:1-depot");

    private static FrontierWorldState conflicted() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:surface-recovery"), 91L));
        var container = state.inventory().containers().get(DEPOT);
        var recovery = state.fencedRecovery();
        var inventory = state.inventory();
        for (var phase : List.of(ContainerSurfaceStatus.PREPARED, ContainerSurfaceStatus.ACTIVE, ContainerSurfaceStatus.CONFLICT)) {
            recovery = FencedRecoveryContainerSupport.transition(recovery, container, inventory.surfaces().get(DEPOT), phase);
            inventory = inventory.withSurfaceStatus(DEPOT, phase);
        }
        var replica = PhysicalReplicaRecord.expected(DEPOT, ReferenceContainerCustody.semanticKind(state, DEPOT),
                7, ReferenceContainerCustody.canonicalFingerprint(state, DEPOT), ReferenceContainerCustody.provenance(DEPOT));
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).fencedRecovery(recovery)
                .replicaCustody(state.replicaCustody().declare(replica)));
    }

    private static ReferenceSurfaceVerified receipt(FrontierWorldState state) {
        var replica = state.replicaCustody().replicas().get(DEPOT);
        var binding = state.fencedRecovery().current().get(FencedRecoveryContainerSupport.bindingId(state.inventory().containers().get(DEPOT)));
        return new ReferenceSurfaceVerified(DEPOT, replica.emittedCanonicalRevision(), replica.replicaRevision(),
                binding.authorityEpoch(), replica.fingerprint(), replica.provenance());
    }

    @Test void currentInspectionRestoresOnlyTheSurfaceAndWakesItsWaitersAcrossReplay() {
        var before = conflicted();
        var observed = receipt(before);
        var payloadCodec = FrontierWorldPayloadCodecs.create();
        var decoded = (ReferenceSurfaceVerified) payloadCodec.decode(observed.type(), payloadCodec.encode(observed));
        assertEquals(observed, decoded);
        var after = ReferenceSurfaceRecovery.verify(before, decoded);
        assertEquals(ContainerSurfaceStatus.ACTIVE, after.inventory().surfaces().get(DEPOT).status());
        var bindingId = FencedRecoveryContainerSupport.bindingId(after.inventory().containers().get(DEPOT));
        assertEquals(FencedRecoveryPhase.RUNNING, after.fencedRecovery().current().get(bindingId).phase());
        assertEquals(before.fencedRecovery().current().get(bindingId).authorityEpoch(),
                after.fencedRecovery().current().get(bindingId).authorityEpoch());
        assertSame(before.replicaCustody(), after.replicaCustody());
        assertSame(before.inventory().fungibleResources(), after.inventory().fungibleResources());
        assertEquals(before.inventory().items(), after.inventory().items());
        var codec = new FrontierWorldStateCodec();
        assertEquals(after, codec.decode(codec.encode(after)));
        var event = new FrontierEvent(1, new EventId("event:surface-verified"), new TransactionId("transaction:surface-verified"),
                after.bootstrap().worldId(), new Revision(8), new SimInstant(100), DEPOT,
                CauseChain.root(new CommandId("command:surface-verified")), decoded);
        assertTrue(FrontierWorldProcessCatalog.wakeKeys(before, after, event).contains(DEPOT));
        var configuration = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition
                .configuration(before.bootstrap().worldId(), 91L);
        var commandId = new CommandId("command:surface-verification");
        var plan = configuration.commandPlanner().plan(before, new FrontierCommand(1, commandId,
                before.bootstrap().worldId(), new Revision(7), new SimInstant(100),
                io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(commandId), decoded));
        var accepted = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class, plan);
        assertEquals(decoded, accepted.events().getFirst().payload());
        assertEquals(after, configuration.reducer().apply(before, event));
        assertThrows(IllegalArgumentException.class, () -> ReferenceSurfaceRecovery.verify(after, observed));
        assertThrows(IllegalArgumentException.class, () -> before.inventory().withSurfaceStatus(DEPOT, ContainerSurfaceStatus.ACTIVE));
    }

    @Test void changedStockForeignProvenanceStaleVersionsAndActualReplicaConflictCannotBeCleared() {
        var state = conflicted();
        var good = receipt(state);
        for (var bad : List.of(
                new ReferenceSurfaceVerified(DEPOT, 7, 1, good.expectedSurfaceEpoch(), "changed-stock", good.provenance()),
                new ReferenceSurfaceVerified(DEPOT, 7, 1, good.expectedSurfaceEpoch(), good.fingerprint(), "foreign-owner"),
                new ReferenceSurfaceVerified(DEPOT, 8, 1, good.expectedSurfaceEpoch(), good.fingerprint(), good.provenance()),
                new ReferenceSurfaceVerified(DEPOT, 7, 2, good.expectedSurfaceEpoch(), good.fingerprint(), good.provenance()),
                new ReferenceSurfaceVerified(DEPOT, 7, 1, good.expectedSurfaceEpoch() + 1, good.fingerprint(), good.provenance())))
            assertThrows(IllegalArgumentException.class, () -> ReferenceSurfaceRecovery.verify(state, bad));
        var mismatched = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(state.replicaCustody()
                .observe(DEPOT, 7, 1, "changed-stock", good.provenance(), 7)));
        assertThrows(IllegalArgumentException.class, () -> ReferenceSurfaceRecovery.verify(mismatched, receipt(mismatched)));
    }

    @Test void releasedColdSuccessorRetainsItsPhysicalPredecessorWithoutOverwritingStock() {
        var before = conflicted();
        var original = receipt(before);
        var scope = ReferenceContainerCustody.scopeId(DEPOT);
        var custody = before.replicaCustody().observe(DEPOT, 7, 1, original.fingerprint(), original.provenance(), 7)
                .acquire(new PhysicalCustodyLease(scope, DEPOT, ReferenceContainerCustody.PROVIDER_ID,
                        1, 7, 2, PhysicalCustodyLeaseStatus.ACQUIRED, null))
                .checkpoint(scope, 1, 7, 2).release(scope, 1, 7, 2)
                .emit(DEPOT, 7, 2, 8, original.fingerprint(), original.provenance());
        var ledger = before.inventory().fungibleResources();
        var account = ledger.accounts().get(scope);
        var lot = account.lotQuantities().keySet().iterator().next();
        var coldStock = ledger.destroy(scope, Map.of(lot, 1), Map.of());
        var advanced = before.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody)
                .inventory(before.inventory().withFungibleResources(coldStock)));
        assertNotEquals(original.fingerprint(), ReferenceContainerCustody.canonicalFingerprint(advanced, DEPOT));
        var verified = ReferenceSurfaceRecovery.verify(advanced, receipt(advanced));
        assertSame(coldStock, verified.inventory().fungibleResources());
        assertSame(custody, verified.replicaCustody());
        assertEquals(ContainerSurfaceStatus.ACTIVE, verified.inventory().surfaces().get(DEPOT).status());
        assertFalse(ReferenceContainerCustody.hasOperationalCustody(verified, DEPOT),
                "normal fenced projection must confirm the COLD successor before another physical consumer writes");
    }
}
