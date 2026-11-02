package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
/** Bounded passive current-purpose/safety projection; never plans a route or judges progress. */
final class FrontierV3EvasionDiagnostic {
    static String fields(FrontierWorldState state,SubjectId actor){
        var retained=state.actorExecutions().actors().get(actor);var current=retained==null?null:retained.current().orElse(null);
        var value=state.combat().evasions().get(actor);var move=state.actorMovements().get(actor);
        double pace=current==null?0:ActorExecutionComposition.CAPABILITIES.require(current.activityKind()).navigationPace(state,current).map(p->p.blocksPerTick()).orElse(0.0);
        return ",\"activityKind\":\""+(current==null?"NONE":current.activityKind().name())+"\",\"requestedPaceBlocksPerTick\":"+pace
                +",\"continuationDepth\":"+(retained==null?0:retained.continuations().size())
                +",\"safety\":{\"requested\":"+(value!=null)+",\"active\":"+(value!=null && value.execution().isPresent())
                +",\"moving\":"+(value!=null && move!=null && value.execution().filter(move.executionId()::equals).isPresent())
                +",\"quietUntil\":"+(value==null?0:value.quietUntil())+"}";
    }
    private FrontierV3EvasionDiagnostic(){}
}
