package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkOrder;
import io.farfrontier.palemirror.frontier.v3.model.FrontierEngineeringWorkSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Read-only last-moment physical precondition; no position, body or effect authority. */
final class FrontierV3EngineeringWorksitePort {
    private FrontierV3EngineeringWorksitePort() { }

    static boolean ready(ServerLevel level, FrontierWorldState state, EngineeringWorkOrder project) {
        if (!FrontierEngineeringWorkSceneSupport.crewReadyForPhysicalWork(state, project)) return false;
        for (var actor : project.engineeringTeam().orElseThrow().memberIds()) {
            var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actor));
            if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()
                    || !FrontierV3ActorBodyController.recognizesRecordedBody(level, state, mob)) return false;
            var station = FrontierEngineeringWorkSceneSupport.workStation(project, actor);
            if (FrontierV3SupportedBodyCapture.observe(level, mob).filter(station.standingBody()::equals).isEmpty()) return false;
        }
        return true;
    }
}
