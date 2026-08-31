package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AmbientActorProcessTest {
    @Test
    void liveBodyDepartureCapturesExactPositionAndHealth() {
        var worldId = new WorldId("frontier:ambient-observation");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, 91L));
        SubjectId resident = new SubjectId("resident:1-1");
        AmbientActorObserved observation = new AmbientActorObserved(resident, new BlockPosition(64, 65, 64), FixedScalar.whole(7));
        var checkpoint = engine.checkpoint();
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:ambient-observation");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(command(worldId, checkpoint, commandId, observation)));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(observation.position(), state.actorLocations().get(resident).position());
        assertEquals(observation.health(), state.actorLocations().get(resident).condition().health());
        assertEquals(observation, FrontierWorldRuntimeDefinition.payloadCodecs().decode(observation.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(observation)));
    }

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

    @Test
    void persistedLeaseOwnsHotExecutionAndClosesOnExactCapture() {
        var worldId = new WorldId("frontier:ambient-lease");
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, 91L));
        SubjectId resident = new SubjectId("resident:1-1");
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        AmbientActorLease lease = AmbientActorProcess.nextLease(initial, resident, engine.checkpoint().instant());
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                engine.submit(command(worldId, engine.checkpoint(), new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:ambient-prepare"), new AmbientLeasePrepared(lease))));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                engine.submit(command(worldId, engine.checkpoint(), new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:ambient-hot"), new AmbientLeaseTransition(resident, AmbientLeaseStatus.HOT))));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Rejected.class,
                engine.submit(command(worldId, engine.checkpoint(), new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:ambient-bad-observation"),
                        new AmbientActorObserved(resident, lease.handoffPosition(), FixedScalar.whole(8)))));
        AmbientLeaseReleased release = new AmbientLeaseReleased(resident, new BlockPosition(65, 64, 64), FixedScalar.whole(8));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                engine.submit(command(worldId, engine.checkpoint(), new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:ambient-draining"), new AmbientLeaseTransition(resident, AmbientLeaseStatus.DRAINING))));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                engine.submit(command(worldId, engine.checkpoint(), new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:ambient-release"), release)));
        FrontierWorldState closed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(AmbientLeaseStatus.CLOSED, closed.ambientLeases().get(resident).status());
        assertEquals(release.position(), closed.actorLocations().get(resident).position());
        assertEquals(closed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(closed)));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command(WorldId worldId,
                                                                                       io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint,
                                                                                       io.farfrontier.palemirror.frontier.v3.api.CommandId commandId,
                                                                                       io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        return new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, worldId, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), payload);
    }
}
