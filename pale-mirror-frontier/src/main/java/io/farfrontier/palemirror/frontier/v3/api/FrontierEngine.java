package io.farfrontier.palemirror.frontier.v3.api;

import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

/** Narrow canonical mutation/read boundary hosted on the Minecraft server thread. */
public interface FrontierEngine<P extends FrontierProjection> {
    CommandResult submit(FrontierCommand command);

    AdvanceResult advanceTo(SimInstant target, WorkBudget budget);

    P projection(ProjectionQuery query);

    CheckpointImage checkpoint();

    EngineStatus status();
}
