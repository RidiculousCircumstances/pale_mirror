package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Family-local retained source/tool obligations. UAE and inventory remain the common authority owners. */
public final class ExtractionWorkAuthority {
    private ExtractionWorkAuthority() { }
    public static ExtractionWork require(FrontierWorldState state, ActorExecutionId execution) {
        var job = state.extractionSites().work().get(execution.activityOwnerId());
        if (execution.activityKind() != ActorActivityKind.EXTRACTION || job == null || job.terminal() || !job.execution().equals(execution))
            throw new IllegalArgumentException("extraction authority lost its exact live work declaration");
        return job;
    }
    public static ExtractionSite site(FrontierWorldState state, ExtractionWork job) {
        var deposit = state.extractionSites().deposits().get(job.siteId());
        if (deposit == null) throw new IllegalArgumentException("extraction work lost its exact site");
        return deposit.site();
    }
    public static int carried(FrontierWorldState state, ExtractionWork job) {
        return ActorCarriedResources.stackQuantity(state.inventory().fungibleResources(), job.execution().actorId(),
                job.carriedAccountId(), site(state, job).settlementId(), job.outputKind());
    }
    public static Optional<SubjectId> pendingOwner(FrontierWorldState state, SubjectId actor) {
        return state.extractionSites().work().values().stream().filter(job -> job.execution().actorId().equals(actor) && job.pending().isPresent())
                .map(ExtractionWork::id).findFirst();
    }
    public static boolean physicalCargo(FrontierWorldState state, ExtractionWork job) {
        return state.inventory().fungibleResources().bindings().values().stream().anyMatch(binding -> binding.accountId().equals(job.carriedAccountId()));
    }
    public static void validateReferences(ExtractionSiteState sites, ActorExecutionState executions) {
        for (var job : sites.work().values()) if (!job.terminal()) executions.requireRetained(job.execution());
        for (var execution : executions.current(ActorActivityKind.EXTRACTION).values()) requireReference(sites, execution);
        for (var execution : executions.suspended()) if (execution.activityKind() == ActorActivityKind.EXTRACTION) requireReference(sites, execution);
    }
    private static void requireReference(ExtractionSiteState sites, ActorExecutionId execution) {
        var job = sites.work().get(execution.activityOwnerId());
        if (job == null || job.terminal() || !job.execution().equals(execution))
            throw new IllegalArgumentException("extraction execution has no exact live job");
    }
    public static WorkProgress labour(FrontierWorldState state, ExtractionWork job, long tick, boolean run) {
        var site = site(state, job); var cell = site.layout().require(job.target().orElseThrow().key().cell());
        var definition = state.bootstrap().ruleset().workCatalog().require(cell.definition().before().kind(), WorkOperation.EXTRACT);
        var prior = job.labour().orElseGet(() -> WorkProgress.pending(definition.workUnits(), tick));
        if (prior.requiredMilliWork() != Math.multiplyExact(definition.workUnits(), 1_000L))
            throw new IllegalArgumentException("extraction labour has a different declared operation");
        prior = prior.pause(tick);
        if (!run || prior.complete()) return prior;
        state.actorExecutions().requireCurrent(job.execution());
        if (job.phase() != ExtractionWork.Phase.EXTRACT || job.pending().isPresent()
                || state.actorMovements().containsKey(job.execution().actorId())
                || !job.movementOrder(site).arrivedAt(state.actorLocations().get(job.execution().actorId()).supportingSurface()))
            throw new IllegalArgumentException("extraction labour needs the exact worker at its admitted source station");
        return prior.resume(tick, ResidentWorkStatistics.speedPermille(state.humanPopulation().resident(job.execution().actorId()), definition,
                state.bootstrap().ruleset().workCatalog()), ResidentWorkIntervals.permittedUntil(state, job.execution().actorId(), tick));
    }
    public static FrontierWorldState pauseLabour(FrontierWorldState state, ExtractionWork job, long tick) {
        if (job.labour().isEmpty() || !job.labour().orElseThrow().running()) return state;
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(
                state.extractionSites().replaceWork(job, job.withLabour(job.labour().orElseThrow().pause(tick)))));
    }
    public static Optional<ActorCarriedResources.Presentation> carriedResources(FrontierWorldState state, HumanAssignment assignment) {
        var job = state.extractionSites().work().get(assignment.ownerId().orElseThrow());
        if (assignment.kind() != HumanAssignmentKind.EXTRACTION || job == null || job.terminal()
                || !job.execution().actorId().equals(assignment.residentId()))
            throw new IllegalArgumentException("extraction carry view lost its exact assignment");
        return carried(state, job) == 0 ? Optional.empty() : Optional.of(new ActorCarriedResources.Presentation(
                job.execution().actorId(), job.carriedAccountId(), new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.OFF)));
    }
}
