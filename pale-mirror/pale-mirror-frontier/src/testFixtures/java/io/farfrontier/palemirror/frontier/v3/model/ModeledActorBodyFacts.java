package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.Optional;

/** Explicit unit/GameTest fixture facts only; never native insertion or death evidence. */
public final class ModeledActorBodyFacts {
    private ModeledActorBodyFacts() { }
    /** Captures the existing modeled cohort; it does not move bodies or certify arrival. */
    /** Captures a declared running fixture's tuple; it does not establish arrival or create a body. */
    public static ActorHotObservation hotObservation(FrontierWorldState state, ActorExecutionId execution, long scopeRevision) {
        var id = new ActorActuationId(ActorBodyAuthority.current(state, execution.actorId()), execution);
        ActorBodyAuthority.requireActuation(state, id);
        return new ActorHotObservation(id, scopeRevision);
    }
    public static ProductionWorkObservation productionObservation(FrontierWorldState state, ProductionJob job,
                                                                 io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId scope) {
        var execution = state.actorExecutions().current(ActorActivityKind.PRODUCTION).get(job.workerId());
        return productionObservation(state, job, scope, execution);
    }
    public static ProductionWorkObservation productionObservation(FrontierWorldState state, ProductionJob job,
                                                                 io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId scope,
                                                                 ActorExecutionId declaredExecution) {
        var id = new ActorActuationId(ActorBodyAuthority.current(state, job.workerId()), declaredExecution);
        return new ProductionWorkObservation(new ActorHotObservation(id, state.sceneLeases().get(scope).revision()),
                job.workTraversal().id(), job.workTraversal().revision(), job.traversalCursor(), job.workProgress(), job.spatial().revision());
    }
    public static SettlementServiceWorkObservation serviceObservation(FrontierWorldState state, SettlementServiceWork work,
                                                                      io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId scope) {
        return serviceObservation(state, work, scope, SettlementServiceExecutionAuthority.current(state, work));
    }
    public static SettlementServiceWorkObservation serviceObservation(FrontierWorldState state, SettlementServiceWork work,
                                                                      io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId scope,
                                                                      ActorExecutionId declaredExecution) {
        var id = new ActorActuationId(ActorBodyAuthority.current(state, work.workerId()), declaredExecution);
        return new SettlementServiceWorkObservation(new ActorHotObservation(id, state.sceneLeases().get(scope).revision()),
                work.phase(), work.inputTraversalCursor(), work.workTraversalCursor(), work.completedWorkTicks(), work.spatial().revision());
    }
    public static FrontierWorldState present(FrontierWorldState state, SubjectId actor) {
        state = ActorBodyAuthority.demand(state, actor);
        var id = ActorBodyAuthority.current(state, actor);
        if (ActorBodyAuthority.require(state, id).phase() == FencedRecoveryPhase.RUNNING) return state;
        var location = state.actorLocations().get(actor);
        return ActorBodyAuthority.present(state, new ActorBodyPresent(id, location.body(), location.condition().health(),
                location.body(), location.condition().health()));
    }
    public static ActorBodyDied death(FrontierWorldState state, SubjectId actor, BodyPosition observed, String cause) {
        var location = state.actorLocations().get(actor);
        var execution = state.actorExecutions().actors().get(actor);
        return new ActorBodyDied(ActorBodyAuthority.current(state, actor), location.body(), location.condition().health(),
                Optional.of(observed), execution == null ? Optional.empty() : execution.current(), cause);
    }
    /** An independent modeled indexed-body observation before a semantic scope receipt. */
    public static FrontierWorldState inspected(FrontierWorldState state, SubjectId actor, BodyPosition observed) {
        var location = state.actorLocations().get(actor);
        var execution = state.actorExecutions().actors().get(actor);
        return ActorBodyAuthority.inspected(state, new ActorBodyInspected(ActorBodyAuthority.current(state, actor),
                ActorBodyInspected.Source.INDEXED_LIVING, location.body(), location.condition().health(),
                observed, location.condition().health(), execution == null ? Optional.empty() : execution.current()));
    }
    /** Independent positive saved-body absence in a model fixture, not scope closure or native disk evidence. */
    public static FrontierWorldState unloaded(FrontierWorldState state, SubjectId actor) {
        var location = state.actorLocations().get(actor);
        var execution = state.actorExecutions().actors().get(actor);
        return ActorBodyAuthority.unloaded(state, new ActorBodyUnloaded(ActorBodyAuthority.current(state, actor),
                location.body(), location.condition().health(), location.body(), location.condition().health(),
                execution == null ? Optional.empty() : execution.current()));
    }
    public static FrontierWorldState died(FrontierWorldState state, SubjectId actor, BodyPosition observed, String cause, long tick) {
        return ActorBodyAuthority.died(state, death(state, actor, observed, cause), FrontierActorDeathConsequences.INSTANCE, tick);
    }
    public static void present(FrontierEngine<FrontierWorldProjection> engine, SubjectId actor) {
        var state = state(engine);
        if (!state.fencedRecovery().current().containsKey(ActorBodyId.recoveryBindingId(actor))) {
            submit(engine, new AmbientLeasePrepared(AmbientActorProcess.nextLease(state, actor, engine.checkpoint().instant())));
            state = state(engine);
        }
        var id = ActorBodyAuthority.current(state, actor);
        if (ActorBodyAuthority.require(state, id).phase() == FencedRecoveryPhase.RUNNING) return;
        var location = state.actorLocations().get(actor);
        submit(engine, new ActorBodyPresent(id, location.body(), location.condition().health(), location.body(), location.condition().health()));
    }
    public static void inspected(FrontierEngine<FrontierWorldProjection> engine, SubjectId actor, BodyPosition observed) {
        var current = state(engine);
        var location = current.actorLocations().get(actor);
        var execution = current.actorExecutions().actors().get(actor);
        submit(engine, new ActorBodyInspected(ActorBodyAuthority.current(current, actor), ActorBodyInspected.Source.INDEXED_LIVING,
                location.body(), location.condition().health(), observed, location.condition().health(),
                execution == null ? Optional.empty() : execution.current()));
    }
    private static FrontierWorldState state(FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }
    private static void submit(FrontierEngine<FrontierWorldProjection> engine, FrontierPayload payload) {
        var checkpoint = engine.checkpoint();
        var id = new CommandId("command:modeled-body-fact-" + checkpoint.revision().value());
        var result = engine.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("modeled body fixture rejected: " + result);
    }
}
