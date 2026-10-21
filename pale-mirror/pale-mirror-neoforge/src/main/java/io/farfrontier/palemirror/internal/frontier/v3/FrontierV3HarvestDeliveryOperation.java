package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import java.util.*;

/** One exact hand/container delivery boundary, shared by ordinary execution and retained aftermath. */
final class FrontierV3HarvestDeliveryOperation {
    record Binding(FrontierV3OperationReceipt.Declaration declaration, List<FrontierV3MinecraftOperation.Participant> participants) { }
    private FrontierV3HarvestDeliveryOperation() { }
    static Binding binding(FrontierWorldState state, FrontierV3ResourceSiteDeliveryWitness witness,
            Mob worker, FrontierV3PhysicalContainer container) {
        if (!worker.getUUID().equals(witness.entityId()) || !container.containerId().equals(witness.containerId()))
            throw new IllegalArgumentException("field delivery substituted its native endpoint");
        var ports = List.of(container.persistence().get(), FrontierV3OperationEntityParticipant.current(worker,
                ActorBodyAuthority.current(state, witness.workerId()).physicalEpoch()));
        return new Binding(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_DELIVERY,
                witness.jobId(), witness.witnessId(), FrontierV3OperationFingerprint.of(witness.write()),
                ports.stream().map(FrontierV3MinecraftOperation.Participant::identity).collect(java.util.stream.Collectors.toSet())), ports);
    }
    static ResourceSiteHarvestDeliveryObservation receipt(FrontierWorldState state, ResourceSiteHarvestJob job,
            FrontierV3ResourceSiteDeliveryWitness witness, List<FungiblePhysicalObservation.Stack> stacks) {
        var custody = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(witness.containerId()));
        if (custody == null || custody.authorityEpoch() != witness.depotEpoch())
            throw new IllegalArgumentException("field delivery lost its retained container epoch");
        return new ResourceSiteHarvestDeliveryObservation(witness.intermediate()
                ? ResourceSiteHarvestBatchDelivered.observationId(job.id(), witness.deliveredYieldBefore())
                : new PhysicalObservationId("observation:field-delivery-" + job.id().value().substring("job:".length())),
                witness.intentId(), witness.siteId(), witness.jobId(), witness.workerId(), job.actorAccountId(), job.depotAccountId(),
                witness.leaseId(), witness.entityId(), witness.resourceEpoch(), witness.quantity(), witness.depotEpoch(), stacks,
                witness.afterFingerprint(), Math.incrementExact(custody.expectedCanonicalRevision()), witness.witnessId());
    }
    static Optional<FrontierV3OperationAftermath> deathEffect(ServerLevel level, FrontierWorldState state,
            ResourceSiteHarvestJob job, Mob body, ActorActuationId identity) {
        var witness = FrontierV3ResourceSiteLedger.get(level).fieldDelivery(job.siteId());
        if (witness == null || !witness.jobId().equals(job.id())) return Optional.empty();
        if (!witness.workerId().equals(job.workerId()) || !witness.entityId().equals(body.getUUID())
                || !witness.intentId().equals(job.intentId()) || !witness.containerId().equals(job.outputSlot().containerId())
                || witness.slot() != job.outputSlot().slot() || witness.intermediate() != job.returningForBatch()
                || witness.deliveredYieldBefore() != job.deliveredYieldQuantity()
                || witness.quantity() != ResourceSiteHarvestCargo.quantity(state, job))
            throw new IllegalStateException("field death delivery witness differs from retained work");
        var source = state.inventory().fungibleResources().bindings().values().stream()
                .filter(value -> value.accountId().equals(job.actorAccountId())).toList();
        if (source.size() != 1 || source.getFirst().authorityEpoch() != witness.resourceEpoch()
                || !source.getFirst().address().equals(new PhysicalStackAddress.ActorHand(job.workerId(), body.getUUID())))
            throw new IllegalStateException("field death delivery lost its exact source binding");
        var container = FrontierV3ResourceSiteDeliveryExecutor.container(level, state, witness.containerId());
        if (container == null) return Optional.empty();
        var custody = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(witness.containerId()));
        var replica = state.replicaCustody().replicas().get(witness.containerId());
        if (custody == null || custody.status() != PhysicalCustodyLeaseStatus.ACQUIRED || custody.authorityEpoch() != witness.depotEpoch()
                || replica == null || replica.state() != PhysicalReplicaState.OBSERVED_CURRENT
                || !replica.fingerprint().equals(witness.beforeFingerprint()) || !container.provenance().equals(replica.provenance())) return Optional.empty();
        var binding = binding(state, witness, body, container);
        boolean applied = after(state, witness, body, container);
        var outcome = new HarvestRetiredDeliveryObserved(identity, job.siteId(), witness.deliveredYieldBefore(), witness.quantity(),
                applied ? Optional.of(receipt(state, job, witness, FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(
                        container.inventory(), state, witness.containerId()))) : Optional.empty());
        return FrontierV3OperationAftermath.capture(level, binding.declaration(), binding.participants(), outcome, applied,
                () -> applied ? after(state, witness, body, container)
                        : ItemStack.matches(body.getOffhandItem(), new ItemStack(Items.WHEAT, witness.quantity()))
                            && FrontierV3ResourceSiteDeliveryExecutor.classify(state, container.inventory(), witness.containerId(),
                                witness.slot(), witness.quantity(), witness) == FrontierV3ResourceSiteDeliveryExecutor.ChestState.BEFORE);
    }
    private static boolean after(FrontierWorldState state, FrontierV3ResourceSiteDeliveryWitness witness,
            Mob body, FrontierV3PhysicalContainer container) {
        return body.getOffhandItem().isEmpty() && FrontierV3ResourceSiteDeliveryExecutor.classify(state, container.inventory(),
                witness.containerId(), witness.slot(), witness.quantity(), witness) == FrontierV3ResourceSiteDeliveryExecutor.ChestState.AFTER;
    }
    static void retireSettled(ServerLevel level, ActorBodyId id, List<FrontierV3OperationAftermath> effects) {
        var ledger = FrontierV3ResourceSiteLedger.get(level); var operations = FrontierV3OperationLedger.get(level);
        for (var effect : effects) {
            if (!(effect.consequence() instanceof HarvestRetiredDeliveryObserved outcome) || !outcome.identity().body().equals(id)) continue;
            if (effect.child().declaration().protocol() != FrontierV3OperationReceipt.Protocol.FIELD_DELIVERY
                    || !effect.child().declaration().owner().equals(outcome.identity().execution().activityOwnerId())
                    || (effect.outcome() == FrontierV3OperationAftermath.Outcome.APPLIED) != outcome.applied().isPresent())
                throw new IllegalStateException("retired field delivery has a foreign operation or outcome");
            var current = operations.find(effect.child().ticket()).orElse(null);
            if (current != null && !current.ownerClosed() || current == null && !operations.prunable(
                    effect.child().nativeReceipt(effect.child().declaration().participants().iterator().next()))) continue;
            var witness = ledger.fieldDelivery(outcome.siteId());
            if (witness == null || !witness.jobId().equals(outcome.identity().execution().activityOwnerId())) continue;
            if (!effect.child().declaration().operation().equals(witness.witnessId())
                    || !effect.child().declaration().fingerprint().equals(FrontierV3OperationFingerprint.of(witness.write())))
                throw new IllegalStateException("settled field delivery substituted its retained witness");
            ledger.retireFieldDelivery(witness); ledger.persist(level);
        }
    }
}
