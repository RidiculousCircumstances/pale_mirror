package io.farfrontier.palemirror.frontier.v3.model;

/** Follow the actual retained work deadline in isolated fixtures, never award fixture labour. */
final class BakeryTestClock {
    private BakeryTestClock() { }
    /** Execute the actual common travel protocol, keeping item effects in their owner. */
    static FrontierWorldState arrive(FrontierWorldState state, ProductionJob job, long tick) {
        var actor = job.workerId();
        var movement = state.actorMovements().get(actor);
        if (movement == null) {
            var started = io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.planCompletion(state,
                    io.farfrontier.palemirror.frontier.v3.process.ProductionProcess.complete(job, tick)).stream()
                    .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                    .filter(io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted.class::isInstance)
                    .map(io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted.class::cast).findFirst();
            if (started.isEmpty()) return state;
            state = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.reduceStarted(state, actor, started.orElseThrow());
        }
        for (int turn = 0; turn < 64 && state.actorMovements().containsKey(actor); turn++) {
            movement = state.actorMovements().get(actor);
            long now = Math.max(tick + 1L, movement.coldTravel()
                    .map(io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute::arrivalTick)
                    .orElse(movement.issuedAtTick() + 1L));
            var events = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.plan(state,
                    io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.progress(movement, now), now);
            var advance = events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                    .filter(io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdAdvanced.class::isInstance)
                    .map(io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdAdvanced.class::cast).findFirst();
            if (advance.isEmpty()) return state; // Other fixture owners must get their turn; never force a service wait.
            state = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.reduceColdAdvanced(state, actor, advance.orElseThrow());
        }
        if (state.actorMovements().containsKey(actor)) throw new AssertionError("fixture travel did not arrive");
        return state;
    }
    static long due(FrontierWorldState state, ProductionJob job, long proposed) {
        var retained = state.productionJobs().get(job.id());
        return retained == null ? proposed : StationWorkLabour.progress(state, retained)
                .filter(WorkProgress::running).map(work -> Math.max(proposed, work.activeUntilTick()))
                .orElse(proposed);
    }
}
