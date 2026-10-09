package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Source family owns effect semantics. UAE, physical custody and inventory own their respective protocols. */
public final class ExtractionPhysicalStateSupport {
    private ExtractionPhysicalStateSupport() { }
    public static FrontierWorldState prepare(FrontierWorldState state, SubjectId subject, ExtractionHotPrepared event, long tick) {
        var job = require(state, subject, event.jobId(), event.step());
        if (job.pending().isPresent()) throw new IllegalArgumentException("mining already retains a non-replayable effect");
        if (!ResidentActivityCoordinator.ordinaryWorkPermitted(state, job.execution().actorId(), tick))
            throw new IllegalArgumentException("mining effect cannot start outside the actor's current work interval");
        switch (event.step()) {
            case ExtractionPhysicalStep.Equipment equipment -> {
                requireStorage(state, job, equipment.containerEpoch());
                var order = ExtractionWorkEffects.equipment(state, job);
                if (!order.exactSlot().equals(equipment.slot())) throw new IllegalArgumentException("mining equipment names a stale current slot");
            }
            case ExtractionPhysicalStep.Cargo cargo -> {
                requireStorage(state, job, cargo.transfer().destinationEpoch());
                var order = ExtractionWorkEffects.cargo(state, job);
                if (!MaterialSourcePreparation.review(state, order).requireReady().equals(cargo.transfer().source()))
                    throw new IllegalArgumentException("mining delivery lost its current resource binding");
                var selected = ContainerMaterialDestination.selectWhole(state, order.containerEndpoint().containerId(), job.outputKind(),
                        ((ActorContainerItemOrder.Portion.Fungible) order.portion()).lotQuantities(), Optional.empty()).orElseThrow(
                        () -> new IllegalArgumentException("mining delivery capacity is unavailable"));
                if (selected.slot() != cargo.transfer().destinationSlot() || selected.before() != cargo.transfer().destinationBefore())
                    throw new IllegalArgumentException("mining delivery has a stale receiving slot");
            }
            case ExtractionPhysicalStep.BlockWork block -> {
                if (job.phase() != ExtractionWork.Phase.EXTRACT || job.labour().isEmpty()
                        || job.labour().orElseThrow().completedAt(tick) < job.labour().orElseThrow().requiredMilliWork()
                        || block.carriedBefore() != ExtractionWorkAuthority.carried(state, job))
                    throw new IllegalArgumentException("physical extraction lacks completed labour or its stack preimage");
                var site = ExtractionWorkAuthority.site(state, job); var cell = site.layout().require(job.target().orElseThrow().key().cell());
                var region = new ExtractionRegion(site.id(), Math.floorDiv(cell.source().x(), 16), Math.floorDiv(cell.source().z(), 16));
                if (state.extractionSites().work().values().stream().anyMatch(other -> !other.id().equals(job.id())
                        && other.siteId().equals(site.id()) && other.pending().orElse(null) instanceof ExtractionPhysicalStep.BlockWork
                        && other.target().filter(target -> region.contains(site.layout().require(target.key().cell()).source())).isPresent()))
                    throw new IllegalArgumentException("source region already fences another non-replayable mining effect");
                var lease = state.replicaCustody().custodyByScope().get(region.scopeId());
                if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                        || !lease.providerId().equals(ExtractionSourceCustody.PROVIDER) || lease.authorityEpoch() != block.sourceEpoch()
                        || !block.extraction().target().equals(cell.source()) || !block.extraction().definition().equals(cell.definition())
                        || !block.extraction().operationId().equals(operation(job))
                        || !block.extraction().output().getFirst().itemKind().equals(job.outputKind()))
                    throw new IllegalArgumentException("physical extraction lacks its exact source, tool rule or custody epoch");
                var tool = state.inventory().items().get(job.toolId());
                if (tool == null || !tool.custody().equals(new InventoryCustody.Actor(job.execution().actorId()))
                        || !tool.itemKind().equals(cell.definition().toolKind()))
                    throw new IllegalArgumentException("mining tool no longer belongs to this actor");
            }
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replaceWork(job, job.prepare(event.step()))));
    }
    public static String operation(ExtractionWork job) { return "extraction:" + job.id().value() + ":" + job.revision(); }
    public static FrontierWorldState observed(FrontierWorldState state, SubjectId subject, ExtractionHotObserved event, long canonicalRevision) {
        var job = require(state, subject, event.jobId(), event.step());
        if (!job.pending().equals(Optional.of(event.step()))) throw new IllegalArgumentException("mining receipt has no exact retained physical intent");
        var changes = FrontierWorldStateUpdate.begin(); var sites = state.extractionSites();
        switch (event.step()) {
            case ExtractionPhysicalStep.Equipment equipment -> {
                requireStorageEpoch(state, job, equipment.containerEpoch());
                var order = ExtractionWorkEffects.equipment(state, job, Optional.of(equipment.slot()));
                changes.inventory(ActorItemCustody.transferObserved(state, order, equipment.containerEpoch(),
                        equipment.containerEpoch(), event.remainingSource(), event.destination()));
                boolean take = job.phase() == ExtractionWork.Phase.TAKE_TOOL;
                var next = job.transition(take ? ExtractionWork.Phase.EXTRACT : ExtractionWork.Phase.FINISHED, take ? job.target() : Optional.empty());
                sites = sites.replaceWork(job, next);
                if (!take) {
                    if (ExtractionWorkAuthority.carried(state, job) != 0) throw new IllegalArgumentException("mining completion retains undelivered output");
                    sites = sites.retire(next.id(), next.revision());
                    changes.actorExecutions(state.actorExecutions().finish(job.execution()));
                }
            }
            case ExtractionPhysicalStep.Cargo cargo -> {
                requireStorageEpoch(state, job, cargo.transfer().destinationEpoch());
                var order = ExtractionWorkEffects.cargo(state, job);
                if (!event.remainingSource().isEmpty() || event.destination().stream().noneMatch(stack ->
                        stack.address().equals(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(
                                order.containerEndpoint().containerId(), cargo.transfer().destinationSlot())))
                        && stack.itemKind().equals(job.outputKind()) && stack.quantity() == cargo.transfer().destinationBefore() + order.portion().quantity()))
                    throw new IllegalArgumentException("mining delivery did not settle its whole held batch in the declared storage slot");
                changes.inventory(ActorItemCustody.transferObserved(state, order, cargo.transfer().sourceEpoch(), cargo.transfer().destinationEpoch(),
                        event.remainingSource(), event.destination()));
                sites = sites.replaceWork(job, job.delivered());
            }
            case ExtractionPhysicalStep.BlockWork block -> {
                var target = job.target().orElseThrow();
                var source = ExtractionWorkAuthority.site(state, job).layout().require(target.key().cell()).source();
                var lease = state.replicaCustody().custodyByScope().get(new ExtractionRegion(job.siteId(),
                        Math.floorDiv(source.x(), 16), Math.floorDiv(source.z(), 16)).scopeId());
                if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                        || !lease.providerId().equals(ExtractionSourceCustody.PROVIDER) || lease.authorityEpoch() != block.sourceEpoch())
                    throw new IllegalArgumentException("mining receipt has a stale source custody epoch");
                var expected = hand(state, job, block.carriedBefore() + block.extraction().output().getFirst().quantity());
                if (!event.remainingSource().isEmpty() || !event.destination().equals(List.of(expected)))
                    throw new IllegalArgumentException("extraction output is not witnessed in its exact worker hand");
                var output = block.extraction().output().getFirst();
                var ledger = state.inventory().fungibleResources().accrueObservedActorExtractionPart(output,
                        ExtractionWorkEffects.output(state, job, output), job.carriedAccountId(), job.execution().actorId(),
                        block.observation().actuation().body().physicalEpoch(), expected);
                var depleted = sites.deposits().get(job.siteId()).extracted(target.key().cell(), target.revision(), block.extraction().operationId());
                if (event.externalSuccessor().isPresent()) depleted = depleted.observe(target.key().cell(), target.revision() + 1,
                        event.externalSuccessor().orElseThrow());
                var next = ExtractionWorkEffects.afterBlock(state, job, depleted, expected.quantity());
                sites = sites.settle(job, next, depleted);
                changes.inventory(state.inventory().withFungibleResources(ledger))
                        .replicaCustody(ExtractionSourceCustody.closeMutation(state, sites, target, canonicalRevision));
            }
        }
        return state.withChanges(changes.extractionSites(sites));
    }
    public static FungiblePhysicalObservation.Stack hand(FrontierWorldState state, ExtractionWork job, int count) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(job.execution().actorId(),
                ActorBodyId.entityId(state.bootstrap().worldId(), job.execution().actorId()), ActorContainerItemOrder.Hand.OFF), job.outputKind(), count);
    }
    private static ExtractionWork require(FrontierWorldState state, SubjectId subject, SubjectId id, ExtractionPhysicalStep step) {
        var job = Objects.requireNonNull(state.extractionSites().work().get(id), "exact extraction effect owner");
        if (!subject.equals(id) || job.terminal() || state.actorMovements().containsKey(job.execution().actorId())
                || !job.movementOrder(ExtractionWorkAuthority.site(state, job)).arrivedAt(
                    state.actorLocations().get(job.execution().actorId()).supportingSurface()))
            throw new IllegalArgumentException("mining effect lacks its exact arrived worker");
        var lease = state.ambientLeases().get(job.execution().actorId());
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT) throw new IllegalArgumentException("mining has no live presentation/body scope");
        step.observation().require(state, job.execution(), job.revision(), state.actorLocations().get(job.execution().actorId()).body());
        return job;
    }
    private static void requireStorage(FrontierWorldState state, ExtractionWork job, long epoch) {
        requireStorageEpoch(state, job, epoch);
        if (!ServiceAccessCoordinator.available(state, ExtractionServiceAccess.identity(state, job)))
            throw new IllegalArgumentException("mining storage service is currently owned by another visitor");
    }
    private static void requireStorageEpoch(FrontierWorldState state, ExtractionWork job, long epoch) {
        var container = ExtractionWorkAuthority.site(state, job).containerId();
        var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container));
        if (lease == null || !ReferenceContainerCustody.hasOperationalCustody(state, container) || lease.authorityEpoch() != epoch
                || ReferenceContainerCustody.blocksCanonicalUse(state, container)) throw new IllegalArgumentException("mining storage custody is stale or unavailable");
    }
    public static FrontierWorldState handCustody(FrontierWorldState state, SubjectId subject, ExtractionHandCustodyObserved event) {
        var job = Objects.requireNonNull(state.extractionSites().work().get(event.jobId()), "mining carried owner");
        if (!subject.equals(job.id()) || job.pending().isPresent() || !job.execution().equals(event.identity().execution())
                || !event.stack().equals(hand(state, job, ExtractionWorkAuthority.carried(state, job))))
            throw new IllegalArgumentException("mining hand boundary lacks its exact retained cargo/body");
        state.actorExecutions().requireRetained(job.execution());
        var body = event.identity().body(); var fence = ActorBodyAuthority.require(state, body);
        var ledger = state.inventory().fungibleResources();
        var bindings = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(job.carriedAccountId())).toList();
        if (event.boundary() == ExtractionHandCustodyObserved.Boundary.MATERIALIZED) {
            if (!bindings.isEmpty()) throw new IllegalArgumentException("mining hand materialization already owns a binding");
            ActorBodyAuthority.requireActuation(state, event.identity());
            ledger = ledger.rebind(job.carriedAccountId(), body.physicalEpoch(),
                    FungiblePhysicalObservation.bind(ledger, job.carriedAccountId(), body.physicalEpoch(), List.of(event.stack())));
        } else {
            if (!body.equals(ActorBodyAuthority.current(state, body.actorId())) || bindings.size() != 1
                    || bindings.getFirst().authorityEpoch() != body.physicalEpoch() || !bindings.getFirst().address().equals(event.stack().address())
                    || !bindings.getFirst().lotQuantities().equals(ledger.accounts().get(job.carriedAccountId()).lotQuantities())
                    || !bindings.getFirst().claimQuantities().isEmpty()
                    || fence.phase() != FencedRecoveryPhase.RUNNING && fence.phase() != FencedRecoveryPhase.AMBIGUOUS)
                throw new IllegalArgumentException("mining saved departure lacks its exact bound cargo");
            ledger = ledger.releaseBindings(job.carriedAccountId(), body.physicalEpoch());
        }
        return state.withInventory(state.inventory().withFungibleResources(ledger));
    }
}
