package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Modeled physical facts for domain unit tests only; never native admission/save evidence. */
final class FrontierTestActorBodies {
    private FrontierTestActorBodies() { }
    static FrontierWorldState present(FrontierWorldState state, SceneLease scene) {
        for (var member : scene.members()) {
            var actor = state.actorLocations().get(member.actorId());
            if (actor.condition().status() != ActorLifeStatus.ALIVE) continue;
            var body = ActorBodyAuthority.current(state, member.actorId());
            if (ActorBodyAuthority.require(state, body).phase() == FencedRecoveryPhase.RUNNING) continue;
            state = ActorBodyAuthority.present(state, new ActorBodyPresent(body, actor.body(), actor.condition().health(),
                    actor.body(), actor.condition().health()));
        }
        return state;
    }
    static void present(FrontierEngine<FrontierWorldProjection> engine, WorldId world, SceneLease scene) {
        for (var member : scene.members()) {
            var state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            var actor = state.actorLocations().get(member.actorId());
            if (actor.condition().status() != ActorLifeStatus.ALIVE) continue;
            var body = ActorBodyAuthority.current(state, member.actorId());
            if (ActorBodyAuthority.require(state, body).phase() == FencedRecoveryPhase.RUNNING) continue;
            var observation = new ActorBodyPresent(body, actor.body(), actor.condition().health(), actor.body(), actor.condition().health());
            var checkpoint = engine.checkpoint();
            var command = new CommandId("command:modeled-body-presence-" + checkpoint.revision().value());
            assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, command, world,
                    checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                    CauseChain.root(command), observation)));
        }
    }
}
