package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AmbientActorProcessTest {
    @Test
    void deathIsExactOwnedEvidenceAndRejectsDuplicate() {
        var worldId = new WorldId("frontier:ambient-death");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, 91L));
        SubjectId resident = new SubjectId("resident:1-1");
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        AmbientActorDied death = new AmbientActorDied(resident, before.actorLocations().get(resident).position(), "entity:test-player");
        var checkpoint = engine.checkpoint();
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:ambient-death");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(command(worldId, checkpoint, commandId, death)));
        FrontierWorldState after = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(ActorLifeStatus.DEAD, after.actorLocations().get(resident).condition().status());
        assertEquals(death, FrontierWorldRuntimeDefinition.payloadCodecs().decode(death.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(death)));
        var duplicate = engine.checkpoint();
        var duplicateId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:ambient-death-duplicate");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class, engine.submit(command(worldId, duplicate, duplicateId, death)));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command(WorldId worldId,
                                                                                       io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint,
                                                                                       io.farfrontier.palemirror.frontier.v3.api.CommandId commandId,
                                                                                       AmbientActorDied payload) {
        return new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, worldId, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), payload);
    }
}
