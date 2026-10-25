package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse;
import java.util.*;

/** Forest owns felling outcomes; common inventory, body and replica owners close their protocols. */
public final class ForestryPhysicalStateSupport {
    private ForestryPhysicalStateSupport() { }
    public static FrontierWorldState prepare(FrontierWorldState state, SubjectId subject, ForestryPhysicalEvent.Prepared event, long tick) {
        var job = require(state, subject, event.job(), event.step());
        if (job.pending().isPresent()) throw new IllegalArgumentException("forestry already retains an unresolved native intent");
        if (job.felling().isEmpty() && !ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.execution().actorId(), tick))
            throw new IllegalArgumentException("forestry cannot start an interaction outside its work interval");
        switch (event.step()) {
            case ForestryPhysicalStep.Equipment equipment -> {
                var transfer = equipment.transfer(); storage(state, job, transfer.containerEpoch(), true);
                var order = ForestryWorkEffects.equipment(state, job);
                if (!order.exactSlot().equals(transfer.slot()) || !((ActorContainerItemOrder.Portion.Exact)order.portion()).item().equals(transfer.item()))
                    throw new IllegalArgumentException("forest equipment preimage no longer matches its exact order");
            }
            case ForestryPhysicalStep.Cargo cargo -> {
                storage(state, job, cargo.transfer().destinationEpoch(), true); var order = ForestryWorkEffects.cargo(state, job);
                if (!MaterialSourcePreparation.review(state, order).requireReady().equals(cargo.transfer().source()))
                    throw new IllegalArgumentException("forest cargo transfer lost its exact held portion");
                var target = ContainerMaterialDestination.selectWhole(state, job.storageTarget().containerId(), job.outputKind(),
                        ((ActorContainerItemOrder.Portion.Fungible)order.portion()).lotQuantities(), Optional.empty()).orElseThrow();
                if (target.slot() != cargo.transfer().destinationSlot() || target.before() != cargo.transfer().destinationBefore())
                    throw new IllegalArgumentException("forest cargo receiving slot changed before preparation");
            }
            case ForestryPhysicalStep.Fell fell -> {
                if (job.felling().isEmpty() || fell.child() != job.appliedEffects() || fell.carriedBefore() != ForestryWorkAuthority.carried(state, job))
                    throw new IllegalArgumentException("forest effect changed its retained child or hand preimage");
                var batch = job.felling().orElseThrow(); var tree = state.forest().require(batch.tree());
                batch.requireProgress(tree, job.appliedEffects(), state.bootstrap().ruleset().forestry());
                var lease = state.replicaCustody().custodyByScope().get(ForestrySourceCustody.scope(batch.tree()));
                if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED || !lease.providerId().equals(ForestrySourceCustody.PROVIDER)
                        || lease.authorityEpoch() != fell.sourceEpoch() || !batch.batch().effects().get(fell.child()).equals(fell.effect()))
                    throw new IllegalArgumentException("forest effect lacks the exact acquired source or prepared consequence");
                var tool = Objects.requireNonNull(state.inventory().items().get(job.toolId()));
                if (!tool.custody().equals(new InventoryCustody.Actor(job.execution().actorId()))
                        || !ToolUse.prepare(tool, state.bootstrap().ruleset().tools(), fell.effect().definition().toolRequirement()).equals(fell.toolUse())
                        || fell.carriedBefore() + fell.effect().output().getFirst().quantity() > state.bootstrap().ruleset().forestry().cargoItems())
                    throw new IllegalArgumentException("forest effect replaced its tool use or exceeded its cargo envelope");
            }
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().forest(state.forest().replaceWork(job, job.prepare(event.step()))));
    }
    public static FrontierWorldState cancelled(FrontierWorldState state, SubjectId subject, ForestryPhysicalEvent.Cancelled event) {
        var job = Objects.requireNonNull(state.forest().work().get(event.job())); var actuation = event.step().observation().actuation();
        state.actorExecutions().requireRetained(actuation.execution()); ActorBodyAuthority.require(state, actuation.body());
        if (!subject.equals(job.id()) || !job.execution().equals(actuation.execution()) || !job.pending().equals(Optional.of(event.step()))
                || job.revision() != event.step().observation().scopeRevision()) throw new IllegalArgumentException("forest cancellation has stale owner/body/intent");
        return state.withChanges(FrontierWorldStateUpdate.begin().forest(state.forest().replaceWork(job, job.observed(event.step()))));
    }
    public static FrontierWorldState observed(FrontierWorldState state, SubjectId subject, ForestryPhysicalEvent.Observed event, long revision) {
        var job = require(state, subject, event.job(), event.step());
        if (!job.pending().equals(Optional.of(event.step()))) throw new IllegalArgumentException("forest outcome has no exact retained intent");
        return settle(state, job, event, revision, false);
    }
    public static FrontierWorldState retired(FrontierWorldState state, SubjectId subject, ForestryPhysicalEvent.Retired event, long revision) {
        var effect = event.effect(); var identity = effect.step().observation().actuation();
        ActorBodyAuthority.requireRetiredDeath(state, identity.body()); state.actorExecutions().requireRetained(identity.execution());
        var job = ForestryWorkAuthority.require(state, identity.execution());
        if (!subject.equals(job.id()) || !effect.job().equals(job.id()) || !job.pending().equals(Optional.of(effect.step()))
                || job.revision() != effect.step().observation().scopeRevision())
            throw new IllegalArgumentException("forest retired outcome changed its exact causal work/body/intent");
        return event.applied() ? settle(state, job, effect, revision, true)
                : state.withChanges(FrontierWorldStateUpdate.begin().forest(state.forest().replaceWork(job, job.observed(effect.step()))));
    }
    private static FrontierWorldState settle(FrontierWorldState state, ForestryWork job, ForestryPhysicalEvent.Observed event, long revision, boolean retired) {
        var settled = job.observed(event.step()); var update = FrontierWorldStateUpdate.begin(); var forest = state.forest();
        switch (event.step()) {
            case ForestryPhysicalStep.Equipment equipment -> {
                var transfer = equipment.transfer(); storage(state, job, transfer.containerEpoch(), false);
                var order = ForestryWorkEffects.equipment(state, job, Optional.of(transfer.slot()));
                update.inventory(ActorItemCustody.transferObserved(state, order, transfer.containerEpoch(), transfer.containerEpoch(), event.remainingSource(), event.destination()));
                boolean take = job.phase() == ForestryWork.Phase.TAKE_TOOL;
                settled = settled.next(take ? (job.target().isPresent() ? ForestryWork.Phase.FELL : ForestryWork.Phase.SELECT_TREE)
                        : ForestryWork.Phase.FINISHED, take ? job.target() : Optional.empty());
                if (!take) {
                    if (ForestryWorkAuthority.carried(state, job) != 0) throw new IllegalArgumentException("forest completion still holds cargo");
                    update.actorExecutions(retired ? ActorExecutionComposition.LIFECYCLE.retire(state.actorExecutions(),
                            job.execution().actorId(), job.execution().activityKind(), job.id()) : state.actorExecutions().finish(job.execution()));
                }
            }
            case ForestryPhysicalStep.Cargo cargo -> {
                var transfer = cargo.transfer(); storage(state, job, transfer.destinationEpoch(), false); var order = ForestryWorkEffects.cargo(state, job);
                if (!event.remainingSource().isEmpty() || event.destination().stream().noneMatch(stack -> stack.address().equals(
                        new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(job.storageTarget().containerId(), transfer.destinationSlot())))
                        && stack.itemKind().equals(job.outputKind()) && stack.quantity() == transfer.destinationBefore()+order.portion().quantity()))
                    throw new IllegalArgumentException("forest cargo is not witnessed in its exact receiving container slot");
                update.inventory(ActorItemCustody.transferObserved(state, order, transfer.sourceEpoch(), transfer.destinationEpoch(), event.remainingSource(), event.destination()));
                settled = settled.delivered(ForestryWorkEffects.partAccount(job, Math.addExact(job.cargoPart(), 1)),
                        ForestryWorkEffects.partLot(job, Math.addExact(job.cargoPart(), 1)));
            }
            case ForestryPhysicalStep.Fell fell -> {
                var batch = job.felling().orElseThrow(); var tree = forest.require(batch.tree());
                batch.requireProgress(tree, job.appliedEffects(), state.bootstrap().ruleset().forestry());
                var lease = state.replicaCustody().custodyByScope().get(ForestrySourceCustody.scope(tree.reference()));
                if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED || !lease.providerId().equals(ForestrySourceCustody.PROVIDER)
                        || lease.authorityEpoch() != fell.sourceEpoch() || fell.child() != job.appliedEffects()
                        || !batch.batch().effects().get(fell.child()).equals(fell.effect())) throw new IllegalArgumentException("forest receipt changed its source epoch/child");
                var output = fell.effect().output().getFirst(); var expected = hand(state, job, fell.carriedBefore()+output.quantity());
                if (!event.remainingSource().isEmpty() || !event.destination().equals(List.of(expected)))
                    throw new IllegalArgumentException("forest consequence is not witnessed in its exact actor hand");
                var ledger = state.inventory().fungibleResources().accrueObservedActorExtractionPart(output, ForestryWorkEffects.output(state, job, output),
                        job.carriedAccount(), job.execution().actorId(), fell.observation().actuation().body().physicalEpoch(), expected);
                int log = tree.declaration().logs().indexOf(tree.declaration().origin(), fell.effect().target());
                var successor = tree.extract(log, fell.effect().operationId());
                if (event.externalSuccessor().isPresent()) successor = successor.external(log);
                forest = forest.replace(tree, successor); settled = settled.childApplied();
                update.inventory(state.inventory().changeCondition(fell.toolUse().before(), fell.toolUse().after().condition()).withFungibleResources(ledger))
                        .replicaCustody(ForestrySourceCustody.closeMutation(state, successor, revision));
            }
        }
        return state.withChanges(update.forest(forest.replaceWork(job, settled)));
    }
    public static FungiblePhysicalObservation.Stack hand(FrontierWorldState state, ForestryWork job, int count) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.execution().actorId(),
                ActorBodyId.entityId(state.bootstrap().worldId(), job.execution().actorId()), ActorContainerItemOrder.Hand.OFF), job.outputKind(), count);
    }
    public static FrontierWorldState handBoundary(FrontierWorldState state, SubjectId subject, ForestryPhysicalEvent.HandBoundary event) {
        var job = Objects.requireNonNull(state.forest().work().get(event.job()));
        if (!subject.equals(job.id()) || job.pending().isPresent() || !job.execution().equals(event.identity().execution())
                || !event.stack().equals(hand(state, job, ForestryWorkAuthority.carried(state, job))))
            throw new IllegalArgumentException("forest hand boundary lost its exact owner/account/stack");
        return ActorWorkCargoCustody.apply(state, job.execution(), job.carriedAccount(), event.identity(), switch(event.boundary()) {
            case MATERIALIZED -> ActorWorkCargoCustody.Boundary.MATERIALIZED;
            case DEPARTED -> ActorWorkCargoCustody.Boundary.SAVED_DEPARTURE;
        }, event.stack());
    }
    private static ForestryWork require(FrontierWorldState state, SubjectId subject, SubjectId id, ForestryPhysicalStep step) {
        var job = Objects.requireNonNull(state.forest().work().get(id), "exact forest physical owner");
        if (!subject.equals(id) || state.actorMovements().containsKey(job.execution().actorId())
                || !job.movementOrder().arrivedAt(state.actorLocations().get(job.execution().actorId()).supportingSurface()))
            throw new IllegalArgumentException("forest effect lacks its exact arrived actor");
        var lease = state.ambientLeases().get(job.execution().actorId());
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT) throw new IllegalArgumentException("forest effect has no live physical body scope");
        step.observation().require(state, job.execution(), job.revision(), state.actorLocations().get(job.execution().actorId()).body()); return job;
    }
    private static void storage(FrontierWorldState state, ForestryWork job, long epoch, boolean starting) {
        var point = ForestryServiceAccess.identity(job).pointId(); var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(point));
        if (lease == null || lease.authorityEpoch() != epoch || !ReferenceContainerCustody.hasOperationalCustody(state, point)
                || ReferenceContainerCustody.blocksCanonicalUse(state, point)
                || starting && !ServiceAccessCoordinator.available(state, ForestryServiceAccess.identity(job)))
            throw new IllegalArgumentException("forest interaction lost its exact operational storage/service custody");
    }
}
