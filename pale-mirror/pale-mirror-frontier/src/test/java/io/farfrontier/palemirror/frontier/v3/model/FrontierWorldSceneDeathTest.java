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


    private static FrontierWorldState state(FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }

    private static void submit(FrontierEngine<FrontierWorldProjection> engine, WorldId world, String suffix, FrontierPayload payload) {
        var checkpoint = engine.checkpoint(); CommandId id = new CommandId("command:scene-multiple-deaths-" + suffix);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, id, world, checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload)));
    }
}
