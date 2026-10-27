package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import static org.junit.jupiter.api.Assertions.*;

/** Shared modeled-body fixture; contains no native movement or arrival assertion. */
final class FrontierV3RuntimeBodyTestFixture {
    private FrontierV3RuntimeBodyTestFixture() { }
    static FrontierWorldState worldState(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow(() -> new AssertionError(
                "runtime has no readable active state at " + runtime.calendarInstant() + ": " + runtime.status())).canonicalState());
    }


    /** Explicit modeled body event for filesystem recovery coverage, not a native movement claim. */
    static void presentModeledBody(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         SubjectId actor, String phase) {
        var state = worldState(runtime); var location = state.actorLocations().get(actor);
        submitWorld(runtime, phase, new ActorBodyPresent(ActorBodyAuthority.current(state, actor),
                location.body(), location.condition().health(), location.body(), location.condition().health()));
    }

    /** Explicit modeled body event for filesystem recovery coverage, not a native movement claim. */
    static void inspectModeledBody(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         SubjectId actor, BodyPosition observed, String phase) {
        var state = worldState(runtime); var location = state.actorLocations().get(actor);
        submitWorld(runtime, phase, new ActorBodyInspected(ActorBodyAuthority.current(state, actor),
                ActorBodyInspected.Source.INDEXED_LIVING, location.body(), location.condition().health(),
                observed, location.condition().health(), state.actorExecutions().actors().get(actor).current()));
    }

    static void submitWorld(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, FrontierPayload payload) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(); CommandId commandId = new CommandId("test:" + phase);
        FrontierCommand command = new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload);
        CommandResult result = runtime.submit(command).orElseThrow();
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
    }

}
