package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;

/** Closes witnessed provider destruction, never guesses disposal from a missing block. */
final class FrontierV3ContainerRemovalExecutor {
    private FrontierV3ContainerRemovalExecutor() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        var operations = FrontierV3OperationLedger.get(level);
        restoreIntents(level, journal, operations);
        for (var witness : journal.removals()) {
            var state = runtime.decodedState().orElseThrow(); var id = witness.container();
            var record = state.inventory().containers().get(id); var surface = state.inventory().surfaces().get(id);
            if (record == null || surface == null || !record.ownerId().equals(witness.owner())
                    || !surface.position().equals(witness.position()))
                throw new IllegalStateException("container removal lost its exact declaration");
            var retained = witness.operation(); var operation = operations.find(retained.ticket()).orElse(null);
            if (operation == null) {
                if (!operations.prunable(retained.nativeReceipt(retained.declaration().participants().iterator().next()))) continue;
                if (retained.cancelled()) { journal.retireRemoval(witness); journal.persist(level); return; }
                if (surface.status() != ContainerSurfaceStatus.RETIRED)
                    throw new IllegalStateException("retired container operation lacks its canonical consequence");
            } else {
                if (!operation.declaration().equals(retained.declaration()) || operation.sequence() != retained.sequence())
                    throw new IllegalStateException("container removal changed its exact native operation");
                if (!FrontierV3MinecraftOperation.recoverOutcome(level, operation.ticket())) continue;
                if (operation.cancelled()) { cancel(level, witness); return; }
            }
            if (surface.status() == ContainerSurfaceStatus.RETIRED) {
                var tombstone = state.fencedRecovery().tombstones().get(FencedRecoveryContainerSupport.bindingId(record));
                if (tombstone == null || tombstone.retiredEpoch() != witness.recoveryEpoch())
                    throw new IllegalStateException("container removal lost its recovery tombstone");
                if (operation != null && !FrontierV3OperationConfirmation.submit(level, runtime, operation.ticket(),
                        new ContainerSurfaceTransition(id, ContainerSurfaceStatus.RETIRED))) continue;
                finish(level, runtime, journal, witness, operation != null); return;
            }
            var p = witness.position();
            var recovery = state.fencedRecovery().current().get(FencedRecoveryContainerSupport.bindingId(record));
            if (recovery == null || recovery.authorityEpoch() != witness.recoveryEpoch()
                    || surface.status() != ContainerSurfaceStatus.ACTIVE)
                throw new IllegalStateException("container removal has stale surface authority");
            if (java.util.stream.IntStream.range(0, record.slotCount()).anyMatch(slot -> state.inventory().itemAt(id, slot).isPresent())
                    || FungibleResourceCustodySupport.accountAtContainer(state, id).filter(account -> !account.lotQuantities().isEmpty()).isPresent()
                    || journal.dropsFrom(id)) continue;
            var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(id));
            if (lease == null || lease.authorityEpoch() != witness.custodyEpoch())
                throw new IllegalStateException("container removal has stale custody authority");
            if (ReferenceContainerCustody.hasLiveCustody(state, id)) {
                long revision = Math.max(runtime.canonicalState().orElseThrow().revision().value(), lease.expectedCanonicalRevision() + 1L);
                if (!(FrontierV3CommandSubmission.submit(runtime, "container-removal-close", id.value(),
                        ReferenceContainerCustody.confirmedMutationTransition(state, id, revision)) instanceof CommandResult.Accepted))
                    return; // The exact removal witness retains the continuation until durability.
            }
            if (!FrontierV3OperationConfirmation.submitRequired(level, runtime, retained.ticket(),
                    new ContainerSurfaceTransition(id, ContainerSurfaceStatus.RETIRED))) return;
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_CONTAINER_RETIRED container={} position={} custodyEpoch={} recoveryEpoch={}",
                    id.value(), p, witness.custodyEpoch(), witness.recoveryEpoch());
            finish(level, runtime, journal, witness, true); return;
        }
    }
    /** Restore only an exact producer-retained intent, never a block/count-based guess.
     * No native replay permission is recreated by rebuilding this family's index. */
    private static void restoreIntents(ServerLevel level, FrontierV3ResourceHandoffLedger journal, FrontierV3OperationLedger operations) {
        for (var operation : operations.probe(FrontierV3OperationReceipt.Protocol.CONTAINER_REMOVAL, 8)) {
            if (operation.ownerClosed()) continue;
            var command = operations.ownerCommand(operation.ticket()).orElseThrow();
            var witness = FrontierV3ContainerRemovalWitness.decode(command.context());
            if (command.closure() != FrontierV3OperationLedger.OwnerCommand.Closure.EXPLICIT_OWNER
                    || !command.consequence().equals(new ContainerSurfaceTransition(witness.container(), ContainerSurfaceStatus.RETIRED))
                    || !witness.operation().ticket().equals(operation.ticket()) || witness.operation().sequence() != operation.sequence()
                    || !witness.operation().declaration().equals(operation.declaration()))
                throw new IllegalStateException("container removal outbox has foreign owner context");
            var existing = journal.removal(witness.container());
            if (existing.isPresent() && !witness.bind(existing.orElseThrow().operation()).equals(existing.orElseThrow()))
                throw new IllegalStateException("container removal index contradicts its retained owner context");
            if (existing.isEmpty() && journal.canPrepareRemoval(witness.container())) {
                journal.prepareRemoval(witness); journal.persist(level);
            }
        }
    }
    private static void finish(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3ResourceHandoffLedger journal, FrontierV3ContainerRemovalWitness witness, boolean retained) {
        journal.retireRemoval(witness); journal.persist(level);
        if (retained && !FrontierV3OperationConfirmation.completeOwner(level, runtime, witness.operation().ticket()))
            throw new IllegalStateException("container retirement lost its canonical acceptance receipt");
    }
    static void cancel(ServerLevel level, FrontierV3ContainerRemovalWitness witness) {
        var operations = FrontierV3OperationLedger.get(level); var journal = FrontierV3ResourceHandoffLedger.get(level);
        var operation = operations.require(witness.operation().ticket());
        if (!operation.cancelled()) throw new IllegalArgumentException("container cancellation lacks positive unused permission");
        var cancelled = witness.bind(operation); journal.updateRemoval(witness, cancelled); journal.persist(level);
        operations.closeOwner(operation.ticket()); operations.retire(operation.ticket()); operations.persist(level);
        journal.retireRemoval(cancelled); journal.persist(level);
    }
}
