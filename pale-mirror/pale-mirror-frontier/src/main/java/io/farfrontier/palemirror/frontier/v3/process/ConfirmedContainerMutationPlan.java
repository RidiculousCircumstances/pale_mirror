package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import java.util.*;

/** The effect owner names its changed containers and supplies its validated successor.
 * Closure is part of the same canonical command, never a later adapter command. */
final class ConfirmedContainerMutationPlan {
    static List<ProposedEvent> close(FrontierWorldState successor, List<SubjectId> containers, long revision) {
        if (new HashSet<>(containers).size() != containers.size())
            throw new IllegalArgumentException("duplicate confirmed container participant");
        var events = new ArrayList<ProposedEvent>();
        for (var container : containers) {
            var closure = ReferenceContainerCustody.confirmedMutationTransition(successor, container, revision);
            successor = ReferenceContainerCustody.closeConfirmedMutation(successor, closure);
            events.add(new ProposedEvent(container, closure));
        }
        return List.copyOf(events);
    }
    private ConfirmedContainerMutationPlan() { }
}
