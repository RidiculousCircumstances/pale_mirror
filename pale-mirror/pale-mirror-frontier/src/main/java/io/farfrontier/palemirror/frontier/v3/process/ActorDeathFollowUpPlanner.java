package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.List;

/** Registered process composition supplies owner events; the body protocol knows no job. */
@FunctionalInterface
interface ActorDeathFollowUpPlanner {
    List<ProposedEvent> plan(FrontierWorldState beforeDeath, FrontierWorldState afterDeath, SubjectId actor);
}
