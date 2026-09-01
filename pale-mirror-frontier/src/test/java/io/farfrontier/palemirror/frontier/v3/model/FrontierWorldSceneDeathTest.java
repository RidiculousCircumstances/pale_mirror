package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Regression coverage for several exact deaths from one physical scene effect. */
class FrontierWorldSceneDeathTest {
    @Test
    void laterExactDeathsRemainDurableWhileTheSameSceneIsDraining() {
        WorldId world = new WorldId("frontier:scene-multiple-deaths");
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(
                FrontierV3FixtureCatalog.routeSceneReturnConfiguration(world, 91L));
        FrontierWorldState initial = state(engine);
        RouteOperation operation = initial.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLeaseId leaseId = new SceneLeaseId("lease:multiple-deaths");
        SceneLease lease = FrontierTestSceneLeases.exact(initial, leaseId, operation.id(), operation.cargoId(),
                operation.currentPosition(), engine.checkpoint().instant(), engine.checkpoint().revision().value(),
                java.util.Optional.empty(), operation.participantIds());
        submit(engine, world, "prepare", new SceneLeasePrepared(lease));
        submit(engine, world, "hot", new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));

        SceneMember first = lease.members().getFirst(), second = lease.members().get(1), third = lease.members().get(2);
        submit(engine, world, "first-death", new ActorDied(leaseId, first.actorId(), lease.memberPosition(first.actorId()), "explosion:test"));
        assertEquals(SceneLeaseStatus.DRAINING, state(engine).sceneLeases().get(leaseId).status());

        submit(engine, world, "second-death", new ActorDied(leaseId, second.actorId(), lease.memberPosition(second.actorId()), "explosion:test"));
        FrontierWorldState afterDeaths = state(engine);
        assertEquals(ActorLifeStatus.DEAD, afterDeaths.actorLocations().get(first.actorId()).condition().status());
        assertEquals(ActorLifeStatus.DEAD, afterDeaths.actorLocations().get(second.actorId()).condition().status());
        assertEquals(SceneLeaseStatus.DRAINING, afterDeaths.sceneLeases().get(leaseId).status());

        submit(engine, world, "third-death", new ActorDied(leaseId, third.actorId(), lease.memberPosition(third.actorId()), "explosion:test"));
        assertEquals(ActorLifeStatus.DEAD, state(engine).actorLocations().get(third.actorId()).condition().status());

        submit(engine, world, "release", new SceneLeaseReleased(leaseId, List.of()));
        FrontierWorldState released = state(engine);
        assertEquals(SceneLeaseStatus.CLOSED, released.sceneLeases().get(leaseId).status());
        assertEquals(OperationStage.FAILED, released.operations().get(operation.id()).stage());
    }

    private static FrontierWorldState state(FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }

    private static void submit(FrontierEngine<FrontierWorldProjection> engine, WorldId world, String suffix, FrontierPayload payload) {
        var checkpoint = engine.checkpoint(); CommandId id = new CommandId("command:scene-multiple-deaths-" + suffix);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, id, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload)));
    }
}
