package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Family reducer: labour, finite depletion and custody settle in one reference-closed update. */
public final class ExtractionColdWork {
    private ExtractionColdWork() { }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ExtractionWorkProgressed event, long tick) {
        var job = Objects.requireNonNull(state.extractionSites().work().get(event.jobId()), "mining work");
        if (!subject.equals(job.id()) || job.terminal() || job.revision() != event.expectedRevision() || job.pending().isPresent()
                || state.actorMovements().containsKey(job.execution().actorId())) throw new IllegalArgumentException("stale mining work operation");
        state.actorExecutions().requireCurrent(job.execution());
        var site = ExtractionWorkAuthority.site(state, job);
        if (event.operation() == ExtractionWorkProgressed.Operation.END_WORK) {
            if (ExtractionWorkPolicy.requested(state, job)
                    || job.phase() != ExtractionWork.Phase.TAKE_TOOL && job.phase() != ExtractionWork.Phase.EXTRACT)
                throw new IllegalArgumentException("mining shutdown lacks a withdrawn settlement mandate");
            if (job.phase() == ExtractionWork.Phase.TAKE_TOOL) {
                var tool = state.inventory().items().get(job.toolId());
                if (tool == null || !(tool.custody() instanceof InventoryCustody.ContainerSlot slot)
                        || !slot.containerId().equals(site.containerId()) || ExtractionWorkAuthority.carried(state, job) != 0)
                    throw new IllegalArgumentException("unstarted mining cannot release retained actor resources");
                var next = job.transition(ExtractionWork.Phase.FINISHED, Optional.empty());
                return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(
                        state.extractionSites().replaceWork(job, next).retire(next.id(), next.revision()))
                        .actorExecutions(state.actorExecutions().finish(job.execution())));
            }
            return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replaceWork(
                    job, job.transition(ExtractionWorkAuthority.carried(state, job) > 0
                            ? ExtractionWork.Phase.STORE : ExtractionWork.Phase.RETURN_TOOL, Optional.empty()))));
        }
        if (event.operation() == ExtractionWorkProgressed.Operation.SELECT_SOURCE) {
            if (job.phase() != ExtractionWork.Phase.SELECT_SOURCE || ExtractionWorkAuthority.carried(state, job) != 0)
                throw new IllegalArgumentException("next mining source requires a settled delivered resource part");
            var target = ExtractionWorkPolicy.nextTarget(state, job);
            if (target.isEmpty() && !ExtractionWorkPolicy.finished(state, job))
                throw new IllegalArgumentException("temporarily unavailable mining frontier is not completion");
            return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replaceWork(
                    job, job.transition(target.isPresent() ? ExtractionWork.Phase.EXTRACT : ExtractionWork.Phase.RETURN_TOOL, target))));
        }
        if (event.operation() == ExtractionWorkProgressed.Operation.SELECT_REACHABLE) {
            var target = ExtractionWorkTargets.alternative(state, job).orElseThrow(
                    () -> new IllegalArgumentException("no reachable alternative extraction cell"));
            return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replaceWork(
                    job, job.transition(ExtractionWork.Phase.EXTRACT, Optional.of(target)))));
        }
        if (!job.movementOrder(site).arrivedAt(state.actorLocations().get(job.execution().actorId()).supportingSurface()))
            throw new IllegalArgumentException("mining operation lacks its exact worker station");
        var update = FrontierWorldStateUpdate.begin();
        switch (event.operation()) {
            case LABOUR -> {
                if (!ResidentActivityCoordinator.mayStartOrdinaryWork(state, job.execution().actorId(), tick))
                    throw new IllegalArgumentException("mining labour is outside its permitted schedule/need interval");
                return state.withChanges(update.extractionSites(state.extractionSites().replaceWork(job,
                        job.withLabour(ExtractionWorkAuthority.labour(state, job, tick, true)))));
            }
            case TAKE_TOOL, STORE, RETURN_TOOL -> {
                if (!ActorExecutionCoordinator.coldAvailable(state, job.execution().actorId())
                        || !ServiceAccessCoordinator.available(state, ExtractionServiceAccess.identity(state, job)))
                    throw new IllegalArgumentException("COLD mining interaction competes with body/service custody");
                if (event.operation() == ExtractionWorkProgressed.Operation.STORE) {
                    if (!state.canReceiveFungible(site.containerId(), job.outputKind(), ExtractionWorkAuthority.carried(state, job)))
                        throw new IllegalArgumentException("mining storage capacity is unavailable");
                    update.inventory(ActorItemCustody.transferCold(state, ExtractionWorkEffects.cargo(state, job)));
                    update.extractionSites(state.extractionSites().replaceWork(job, job.delivered()));
                } else {
                    boolean take = event.operation() == ExtractionWorkProgressed.Operation.TAKE_TOOL;
                    if (job.phase() != (take ? ExtractionWork.Phase.TAKE_TOOL : ExtractionWork.Phase.RETURN_TOOL))
                        throw new IllegalArgumentException("equipment operation does not match mining phase");
                    update.inventory(ActorItemCustody.transferCold(state, ExtractionWorkEffects.equipment(state, job)));
                    var next = job.transition(take ? ExtractionWork.Phase.EXTRACT : ExtractionWork.Phase.FINISHED,
                            take ? job.target() : Optional.empty());
                    var sites = state.extractionSites().replaceWork(job, next);
                    if (!take) {
                        if (ExtractionWorkAuthority.carried(state, job) != 0) throw new IllegalArgumentException("mining cannot finish with unsettled cargo");
                        sites = sites.retire(next.id(), next.revision());
                        update.actorExecutions(state.actorExecutions().finish(job.execution()));
                    }
                    update.extractionSites(sites);
                }
                return state.withChanges(update);
            }
            case EXTRACT_BLOCK -> {
                if (job.phase() != ExtractionWork.Phase.EXTRACT || !ActorExecutionCoordinator.coldAvailable(state, job.execution().actorId())
                        || ExtractionSourceCustody.blocksCold(state, job.target().orElseThrow())
                        || ReferenceContainerCustody.hasLiveCustody(state, site.containerId())
                        || ReferenceContainerCustody.blocksCanonicalUse(state, site.containerId())
                        || job.labour().isEmpty() || job.labour().orElseThrow().completedAt(tick) != job.labour().orElseThrow().requiredMilliWork())
                    throw new IllegalArgumentException("COLD extraction lacks completed labour or exclusive known source custody");
                var tool = state.inventory().items().get(job.toolId());
                if (tool == null || !tool.custody().equals(new InventoryCustody.Actor(job.execution().actorId())))
                    throw new IllegalArgumentException("miner does not hold its admitted tool");
                var deposit = state.extractionSites().deposits().get(job.siteId());
                var target = job.target().orElseThrow(); var cell = site.layout().require(target.key().cell());
                var effect = BlockExtraction.prepareKnown("extraction:" + job.id().value() + ":" + job.revision(), job.execution(),
                        cell.source(), cell.definition(), deposit.cells().get(cell.id()).knownBlock());
                var output = effect.output().getFirst();
                if (effect.output().size() != 1 || !output.itemKind().equals(job.outputKind()))
                    throw new IllegalArgumentException("mining batch output differs from its declared held resource");
                var ledger = state.inventory().fungibleResources().accrueColdActorExtractionPart(output,
                        ExtractionWorkEffects.output(state, job, output), job.carriedAccountId(), job.execution().actorId());
                var depleted = deposit.extracted(cell.id(), target.revision(), effect.operationId());
                var next = ExtractionWorkEffects.afterBlock(state, job, depleted, ExtractionWorkAuthority.carried(state, job) + output.quantity());
                return state.withChanges(update.inventory(state.inventory().withFungibleResources(ledger))
                        .extractionSites(state.extractionSites().settle(job, next, depleted)));
            }
        }
        throw new IllegalArgumentException("unhandled extraction operation");
    }
}
