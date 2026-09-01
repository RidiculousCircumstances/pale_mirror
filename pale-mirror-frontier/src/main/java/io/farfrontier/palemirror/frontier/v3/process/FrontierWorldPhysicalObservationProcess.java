package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;

import java.util.ArrayList;
import java.util.List;

/** Pure command/reducer boundary for post-effect physical evidence. */
public final class FrontierWorldPhysicalObservationProcess {
    private FrontierWorldPhysicalObservationProcess() { }

    static CommandPlan plan(FrontierWorldState state, PhysicalDeltaObserved observed, long submittedAt) {
        FrontierWorldState after;
        try { after = state.recordPhysicalDelta(observed.delta()); }
        catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        List<ProposedEvent> events = new ArrayList<>();
        events.add(new ProposedEvent(FrontierExecutionSubjects.PHYSICAL_EXECUTOR, observed));
        if (isKnownRouteSurfaceLoss(observed.delta())) {
            for (Settlement settlement : after.bootstrap().settlements()) {
                if (!after.routeTopology().supplyPassable(after.bootstrap(), settlement.id())) {
                    var reconsideration = StrategicObjectiveProcess.routeReconsideration(settlement.id(), observed.delta().position(), "loss", Math.addExact(submittedAt, 1L));
                    events.add(new ProposedEvent(settlement.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(reconsideration)));
                }
            }
        }
        return new CommandPlan.Accepted(List.copyOf(events));
    }

    static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                     PhysicalDeltaObserved observed) {
        if (!subject.equals(FrontierExecutionSubjects.PHYSICAL_EXECUTOR)) throw new IllegalArgumentException("physical delta lacks trusted executor subject");
        return state.recordPhysicalDelta(observed.delta());
    }

    private static boolean isKnownRouteSurfaceLoss(PhysicalDelta delta) {
        return delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS && delta.ownerId().filter(FrontierRouteNetwork.OWNER::equals).isPresent()
                && delta.semanticPart().filter(GrayboxSemanticPart.ROUTE_SURFACE::equals).isPresent();
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
        if (!state.containerSlotAvailable(slot)) {
            throw new IllegalArgumentException("resource deposit targets an occupied canonical slot");
        }
        return state.withInventory(state.inventory().store(deposited.item()));
    }
}
