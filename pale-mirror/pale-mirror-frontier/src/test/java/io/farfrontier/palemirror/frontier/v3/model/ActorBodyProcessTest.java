package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorBodyProcessTest {
    @Test void registeredPhysicalBoundaryCommitsOneExactReleaseAndRejectsItsLateDuplicate() {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:body-release"), 91L);
        var actor = base.initialState().humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var state = ActorBodyAuthority.demand(base.initialState(), actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator(),
                base.executionMetrics(), base.kernelQuarantineReporter());
        var engine = FrontierEngines.create(configuration);
        var before = engine.checkpoint();
        var obsolete = new ActorBodyReleased(new ActorBodyId(actor, body.physicalEpoch() + 1L));
        var rejected = engine.submit(command(before, "command:body-wrong-epoch", obsolete));
        assertInstanceOf(CommandResult.Rejected.class, rejected);
        assertEquals(before.revision(), engine.checkpoint().revision());
        assertArrayEquals(before.canonicalState(), engine.checkpoint().canonicalState());
        assertInstanceOf(CommandResult.Accepted.class,
                engine.submit(command(before, "command:body-release", new ActorBodyReleased(body))));
        var after = engine.checkpoint();
        var recovered = base.stateCodec().decode(after.canonicalState());
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(recovered, actor));
        assertEquals(state.actorLocations(), recovered.actorLocations());
        assertEquals(state.actorExecutions(), recovered.actorExecutions());
        assertEquals(state.inventory(), recovered.inventory());
        assertInstanceOf(CommandResult.Rejected.class,
                engine.submit(command(after, "command:body-late-release", new ActorBodyReleased(body))));
        assertEquals(after.revision(), engine.checkpoint().revision());
        assertArrayEquals(after.canonicalState(), engine.checkpoint().canonicalState());
    }
    private static FrontierCommand command(CheckpointImage checkpoint, String id, FrontierPayload payload) {
        var commandId = new CommandId(id);
        return new FrontierCommand(FrontierCommand.LEGACY_SCHEMA_VERSION, commandId, checkpoint.worldId(),
                checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(commandId), payload);
    }
}
