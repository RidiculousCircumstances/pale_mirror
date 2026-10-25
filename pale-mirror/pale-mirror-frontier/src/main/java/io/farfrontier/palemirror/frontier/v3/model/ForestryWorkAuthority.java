package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import java.util.*;

/** Family-issued exact work facts; shared life/navigation/custody never inspect forestry internals. */
public final class ForestryWorkAuthority {
    private ForestryWorkAuthority() { }
    public static ForestryWork require(FrontierWorldState state, ActorExecutionId execution) {
        var job = state.forest().work().get(execution.activityOwnerId());
        if (execution.activityKind() != ActorActivityKind.FORESTRY || job == null || job.terminal() || !job.execution().equals(execution))
            throw new IllegalArgumentException("forestry execution lost its exact current work");
        return job;
    }
    public static void validateReferences(ForestState forest, ActorExecutionState executions, HumanPopulation population) {
        for (var job : forest.work().values()) {
            executions.requireRetained(job.execution()); var resident = population.resident(job.execution().actorId());
            if (resident == null || !resident.settlementId().equals(job.home()))
                throw new IllegalArgumentException("forestry work lost its exact home resident");
        }
        for (var execution : executions.current(ActorActivityKind.FORESTRY).values()) requireReference(forest, execution);
        for (var execution : executions.suspended()) if (execution.activityKind() == ActorActivityKind.FORESTRY) requireReference(forest, execution);
    }
    private static void requireReference(ForestState forest, ActorExecutionId execution) {
        var job = forest.work().get(execution.activityOwnerId());
        if (job == null || !job.execution().equals(execution) || job.terminal())
            throw new IllegalArgumentException("forestry activity has no exact retained owner");
    }
    public static int carried(FrontierWorldState state, ForestryWork job) {
        return ActorCarriedResources.stackQuantity(state.inventory().fungibleResources(), job.execution().actorId(),
                job.carriedAccount(), job.home(), job.outputKind());
    }
    public static boolean physicalCargo(FrontierWorldState state, ForestryWork job) {
        return state.inventory().fungibleResources().bindings().values().stream().anyMatch(value -> value.accountId().equals(job.carriedAccount()));
    }
    public static Optional<SubjectId> pendingOwner(FrontierWorldState state, SubjectId actor) {
        return state.forest().work().values().stream().filter(job -> job.execution().actorId().equals(actor) && job.pending().isPresent())
                .map(ForestryWork::id).findFirst();
    }
    public static Optional<ActorCarriedResources.Presentation> carriedResources(FrontierWorldState state, HumanAssignment assignment) {
        var job = state.forest().work().get(assignment.ownerId().orElseThrow());
        if (assignment.kind() != HumanAssignmentKind.FORESTRY || job == null || !job.execution().actorId().equals(assignment.residentId()))
            throw new IllegalArgumentException("forestry carry view lost its exact assignment");
        return carried(state, job) == 0 ? Optional.empty() : Optional.of(new ActorCarriedResources.Presentation(
                job.execution().actorId(), job.carriedAccount(), new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.OFF)));
    }
    public static WorkProgress labour(FrontierWorldState state, ForestryWork job, long tick, boolean run) {
        var target = job.target().orElseThrow(); var tree = ForestKnowledge.require(state, target.tree());
        if (tree.revision() != target.sourceRevision() || tree.remaining() != target.expectedLogs())
            throw new IllegalArgumentException("forestry labour has a changed source declaration");
        long units = 0; WorkCatalog.Definition rateDefinition = null;
        for (int i = 0; i < tree.declaration().logs().size(); i++) if (tree.present(i)) {
            var definition = state.bootstrap().ruleset().workCatalog().require(tree.declaration().logs().block(i).kind(), WorkOperation.EXTRACT);
            if (definition.capability() != HumanCapability.EXTRACTION) throw new IllegalArgumentException("tree work capability is undeclared");
            units = Math.addExact(units, definition.workUnits()); rateDefinition = definition;
        }
        var prior = job.labour().orElse(WorkProgress.pending(units, tick)).pause(tick);
        if (prior.requiredMilliWork() != Math.multiplyExact(units, 1000)) throw new IllegalArgumentException("forestry work norm changed");
        if (!run || prior.complete()) return prior;
        state.actorExecutions().requireCurrent(job.execution());
        if (job.phase() != ForestryWork.Phase.FELL || job.pending().isPresent() || job.felling().isPresent()
                || state.actorMovements().containsKey(job.execution().actorId())
                || !job.movementOrder().arrivedAt(state.actorLocations().get(job.execution().actorId()).supportingSurface()))
            throw new IllegalArgumentException("forestry labour requires its actual retained work station");
        int workerRate = ResidentWorkStatistics.speedPermille(state.humanPopulation().resident(job.execution().actorId()),
                Objects.requireNonNull(rateDefinition), state.bootstrap().ruleset().workCatalog());
        int rate = io.farfrontier.palemirror.frontier.v3.model.tooling.ToolWorkRate.speedPermille(workerRate,
                state.bootstrap().ruleset().tools(), state.inventory().items().get(job.toolId()), state.bootstrap().ruleset().forestry().tool());
        return prior.resume(tick, rate, ResidentWorkIntervals.permittedUntil(state, job.execution().actorId(), tick));
    }
    public static FrontierWorldState pauseLabour(FrontierWorldState state, ForestryWork job, long tick) {
        if (job.labour().isEmpty() || !job.labour().orElseThrow().running() || job.felling().isPresent()) return state;
        return state.withChanges(FrontierWorldStateUpdate.begin().forest(state.forest().replaceWork(job,
                job.withLabour(job.labour().orElseThrow().pause(tick)))));
    }
}
