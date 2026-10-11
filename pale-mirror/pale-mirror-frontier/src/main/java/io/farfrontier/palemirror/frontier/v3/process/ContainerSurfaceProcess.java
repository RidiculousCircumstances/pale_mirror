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
        if (activationBlocked(state, transition)) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY,
                    "container activation waits for its exact cold production input"));
        }
        try {
            requireRetirement(state, transition);
            FencedRecoveryContainerSupport.transition(state.fencedRecovery(), container,
                    state.inventory().surfaces().get(transition.containerId()), transition.status());
        } catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(container.ownerId(), transition)));
    }

    static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                     ContainerSurfaceTransition transition) {
        ContainerRecord container = state.inventory().containers().get(transition.containerId());
        if (container == null || !subject.equals(container.ownerId())) throw new IllegalArgumentException("container surface lacks its canonical owner");
        if (activationBlocked(state, transition)) {
            throw new IllegalArgumentException("container activation waits for its exact cold production input");
        }
        requireRetirement(state, transition);
        FrontierWorldState reduced = state.withInventory(state.inventory().withSurfaceStatus(transition.containerId(), transition.status()));
        return reduced.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                FencedRecoveryContainerSupport.transition(state.fencedRecovery(), container,
                        state.inventory().surfaces().get(transition.containerId()), transition.status())));
    }

    private static boolean activationBlocked(FrontierWorldState state, ContainerSurfaceTransition transition) {
        return transition.status() == ContainerSurfaceStatus.ACTIVE
                && ContainerSurfaceActivationStateSupport.blockedByColdProduction(state, transition.containerId());
    }

    private static void requireRetirement(FrontierWorldState state, ContainerSurfaceTransition transition) {
        if (transition.status() != ContainerSurfaceStatus.RETIRED) return;
        var id = transition.containerId();
        if (!state.inventory().surfaces().get(id).fixed()
                || ReferenceContainerCustody.hasLiveCustody(state, id)
                || state.inventory().items().values().stream().anyMatch(item ->
                    item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(id))
                || state.inventory().fungibleResources().accounts().values().stream().anyMatch(account ->
                    account.custody().equals(new ResourceCustody.Container(id))
                    && (!account.lotQuantities().isEmpty() || !account.claimQuantities().isEmpty()))
                || state.inventory().fungibleResources().bindings().values().stream().anyMatch(binding ->
                    binding.address() instanceof PhysicalStackAddress.ContainerSlot slot && slot.slot().containerId().equals(id)))
            throw new IllegalArgumentException("container retirement requires disposed stock and closed exact custody");
    }
}
