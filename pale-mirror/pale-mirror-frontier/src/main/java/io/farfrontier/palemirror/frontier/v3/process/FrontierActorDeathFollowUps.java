package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.ArrayList;
import java.util.List;

/** Resource/effect settlement follows the already reference-closed death acknowledgement. */
final class FrontierActorDeathFollowUps {
    private FrontierActorDeathFollowUps() { }
    static List<ProposedEvent> plan(FrontierWorldState beforeDeath, FrontierWorldState state, SubjectId actor) {
        var events = new ArrayList<ProposedEvent>();
        events.addAll(ResidentMealProcess.cancelRetiredMealContinuation(beforeDeath, state, actor));
        events.addAll(ProductionProcess.failPreEffectWorkForDeath(state, actor));
        return List.copyOf(events);
    }
}
