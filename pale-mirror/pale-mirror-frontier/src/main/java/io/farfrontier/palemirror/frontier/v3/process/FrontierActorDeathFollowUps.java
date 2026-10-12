package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.ArrayList;
import java.util.List;

/** Resource/effect settlement follows the already reference-closed death acknowledgement. */
final class FrontierActorDeathFollowUps {
    private static final List<ActorDeathFollowUpPlanner> OWNERS = List.of(
            (before, after, actor, command) -> ResidentMealProcess.cancelRetiredMealContinuation(before, after, actor),
            (before, after, actor, command) -> ProductionProcess.failPreEffectWorkForDeath(after, actor),
            MedicalTreatmentProcess::retirePreEffectForDeath);
    private FrontierActorDeathFollowUps() { }
    static List<ProposedEvent> plan(FrontierWorldState beforeDeath, FrontierWorldState state, SubjectId actor,
                                   io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        var events = new ArrayList<ProposedEvent>();
        for (var owner : OWNERS) events.addAll(owner.plan(beforeDeath, state, actor, command));
        return List.copyOf(events);
    }
}
