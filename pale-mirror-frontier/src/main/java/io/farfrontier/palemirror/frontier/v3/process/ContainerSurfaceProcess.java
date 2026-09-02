package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;

import java.util.List;

/** Canonical acceptance and reduction boundary for persisted container-surface evidence. */
public final class ContainerSurfaceProcess {
    private ContainerSurfaceProcess() { }

    static CommandPlan plan(FrontierWorldState state, ContainerSurfaceTransition transition) {
        ContainerRecord container = state.inventory().containers().get(transition.containerId());
        if (container == null || !state.inventory().surfaces().containsKey(transition.containerId())) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, "container surface is unknown"));
        }
        if (transition.status() == ContainerSurfaceStatus.ACTIVE
                && ContainerSurfaceActivationStateSupport.blockedByColdProduction(state, transition.containerId())) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY,
                    "container activation waits for its exact cold production input"));
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(container.ownerId(), transition)));
    }

    static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                     ContainerSurfaceTransition transition) {
        ContainerRecord container = state.inventory().containers().get(transition.containerId());
        if (container == null || !subject.equals(container.ownerId())) throw new IllegalArgumentException("container surface lacks its canonical owner");
        return state.withInventory(state.inventory().withSurfaceStatus(transition.containerId(), transition.status()));
    }
}
