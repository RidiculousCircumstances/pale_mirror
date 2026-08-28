package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;

import java.util.List;

/** Pure command/reducer boundary for post-effect physical evidence. */
final class FrontierWorldPhysicalObservationProcess {
    private FrontierWorldPhysicalObservationProcess() { }

    static CommandPlan plan(FrontierWorldState state, PhysicalDeltaObserved observed) {
        try { state.recordPhysicalDelta(observed.delta()); }
        catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, observed)));
    }

    static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                     PhysicalDeltaObserved observed) {
        if (!subject.equals(FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR)) throw new IllegalArgumentException("physical delta lacks trusted executor subject");
        return state.recordPhysicalDelta(observed.delta());
    }

    static CommandPlan planResourceDeposit(FrontierWorldState state, ResourceDeposited deposited) {
        InventoryCustody.ContainerSlot slot = (InventoryCustody.ContainerSlot) deposited.item().custody();
        ContainerRecord container = state.inventory().containers().get(slot.containerId());
        if (container == null) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, "resource deposit has an unknown container"));
        }
        try { reduceResourceDeposit(state, container.ownerId(), deposited); }
        catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(container.ownerId(), deposited)));
    }

    static FrontierWorldState reduceResourceDeposit(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                     ResourceDeposited deposited) {
        InventoryCustody.ContainerSlot slot = (InventoryCustody.ContainerSlot) deposited.item().custody();
        ContainerRecord container = state.inventory().containers().get(slot.containerId());
        if (container == null || !subject.equals(container.ownerId())) {
            throw new IllegalArgumentException("resource deposit lacks the owned container subject");
        }
        if (!deposited.item().economicOwnerId().equals(container.ownerId())) {
            throw new IllegalArgumentException("resource deposit claim does not belong to its receiving container owner");
        }
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) {
            throw new IllegalArgumentException("resource deposit needs an active owned container surface");
        }
        if (state.inventory().itemAt(slot.containerId(), slot.slot()).isPresent()) {
            throw new IllegalArgumentException("resource deposit targets an occupied canonical slot");
        }
        return state.withInventory(state.inventory().store(deposited.item()));
    }
}
