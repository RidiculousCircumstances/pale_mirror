package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One exact selected equipment transfer. Storage selection and walking belong to the retained visit. */
public final class EngineeringEquipmentProcess {
    private EngineeringEquipmentProcess() { }

    /** The retained service visit still needs its receipt to acknowledge the clearance successor. */
    public static boolean mayCompact(FrontierWorldState state, PhysicalIntent intent) {
        if ((intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
                && intent.status() == PhysicalIntentStatus.CONFIRMED) {
            var declaration = EquipmentServiceBoundary.capability(intent);
            var visit = EngineeringWorkOrderSupport.find(state, declaration.ownerId(intent))
                    .flatMap(EngineeringWorkOrder::assembly).flatMap(EngineeringWorkAssembly::equipmentVisit).orElse(null);
            if (visit != null && !visit.clearing() && visit.operation() == intent.kind()
                    && visit.actorId().equals(declaration.actorId(intent))
                    && visit.itemId().equals(intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT))
                    && visit.access().containerId().equals(EquipmentPhysicalAuthority.container(intent))) {
                var owner = EngineeringWorkOrderSupport.require(state, declaration.ownerId(intent));
                if (intent.id().equals(visit.transferId(owner.id(), EngineeringExecutionAuthority.assemblyCurrent(state, owner)
                        .requireMember(visit.actorId()).generation()))) return false;
            }
        }
        return EquipmentItemDeparture.mayCompact(state, intent);
    }

    public static Optional<PhysicalIntent> issueOne(FrontierWorldState state, EngineeringWorkOrder owner) {
        var visit = owner.assembly().flatMap(EngineeringWorkAssembly::equipmentVisit).orElse(null);
        if (visit == null || visit.operation() != PhysicalIntentKind.EQUIPMENT_ISSUE || visit.clearing()
                || !owner.building() || pending(state, owner.id(), PhysicalIntentKind.EQUIPMENT_ISSUE)
                || !EngineeringDepotService.available(state, owner, visit)
                || !EngineeringDepotService.atStations(state, owner, EngineeringJourneyPurpose.MUSTER_DEPOT)) return Optional.empty();
        var position = state.inventory().surfaces().get(visit.access().containerId()).position();
        return Optional.of(new PhysicalIntent(visit.transferId(owner.id(), EngineeringExecutionAuthority.assemblyCurrent(state, owner)
                .requireMember(visit.actorId()).generation()),
                PhysicalIntentKind.EQUIPMENT_ISSUE, PhysicalIntentStatus.PREPARED, owner.settlementId(),
                PhysicalIntentRoleBinding.engineeringEquipmentIssue(owner.id(), visit.actorId(), visit.itemId(), visit.access().containerId()),
                fixed(position), 0, PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED,
                new EquipmentServiceRequest(EngineeringEquipmentServiceAccess.identity(owner, visit), visit.requestedAtTick()),
                PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE));
    }

    public static Optional<PhysicalIntent> returnOne(FrontierWorldState state, EngineeringWorkOrder owner) {
        ReturnReadiness readiness = returnReadiness(state, owner);
        if (!readiness.ready()) return Optional.empty();
        var actor = readiness.actorId().orElseThrow();
        var item = readiness.itemId().orElseThrow();
        var slot = readiness.targetSlot().orElseThrow();
        var position = state.inventory().surfaces().get(slot.containerId()).position();
        var visit = owner.assembly().orElseThrow().equipmentVisit().orElseThrow();
        return Optional.of(new PhysicalIntent(visit.transferId(owner.id(), EngineeringExecutionAuthority.assemblyCurrent(state, owner)
                .requireMember(actor).generation()),
                PhysicalIntentKind.EQUIPMENT_RETURN, PhysicalIntentStatus.PREPARED, owner.settlementId(),
                PhysicalIntentRoleBinding.engineeringEquipmentReturn(owner.id(), actor, item, slot.containerId()), fixed(position), 0,
                PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED, slot,
                new EquipmentServiceRequest(EngineeringEquipmentServiceAccess.identity(owner, visit), visit.requestedAtTick()),
                PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE));
    }

    /** Same retained facts for admission and diagnosis; this never reranks storage or free slots. */
    public static ReturnReadiness returnReadiness(FrontierWorldState state, EngineeringWorkOrder owner) {
        var visit = owner.assembly().flatMap(EngineeringWorkAssembly::equipmentVisit).orElse(null);
        var container = visit == null ? Optional.<SubjectId>empty() : Optional.of(visit.access().containerId());
        if (owner.engineeringTeam().isEmpty()) return ReturnReadiness.unready("NO_ENGINEERING_TEAM", container);
        if (!owner.readyForToolReturn()) return ReturnReadiness.unready("NOT_READY_FOR_RETURN", container);
        if (pending(state, owner.id(), PhysicalIntentKind.EQUIPMENT_RETURN)) return ReturnReadiness.unready("RETURN_INTENT_PENDING", container);
        if (returnedOrLost(state, owner)) return ReturnReadiness.unready("NO_EXACT_TEAM_TOOL", container);
        if (visit == null || visit.operation() != PhysicalIntentKind.EQUIPMENT_RETURN)
            return ReturnReadiness.unready("NO_SELECTED_RETURN_VISIT", container);
        if (visit.clearing()) return ReturnReadiness.unready("CLEARING_SERVICE_ACCESS", container);
        if (!EngineeringDepotService.available(state, owner, visit)) return ReturnReadiness.unready("SELECTED_RETURN_UNAVAILABLE", container);
        if (!EngineeringDepotService.atStations(state, owner, EngineeringJourneyPurpose.RETURN_DEPOT))
            return ReturnReadiness.unready("TOOL_HOLDER_NOT_AT_STORAGE_SERVICE_PORT", container);
        return new ReturnReadiness("READY", container, Optional.of(visit.actorId()), Optional.of(visit.itemId()), visit.returnSlot());
    }

    public record ReturnReadiness(String reason, Optional<SubjectId> containerId, Optional<SubjectId> actorId,
                                  Optional<SubjectId> itemId, Optional<PhysicalContainerSlot> targetSlot) {
        public ReturnReadiness {
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("return readiness reason is required");
            Objects.requireNonNull(containerId); Objects.requireNonNull(actorId); Objects.requireNonNull(itemId); Objects.requireNonNull(targetSlot);
            boolean ready = reason.equals("READY");
            if (ready != (actorId.isPresent() && itemId.isPresent() && targetSlot.isPresent())
                    || ready && (containerId.isEmpty() || !targetSlot.orElseThrow().containerId().equals(containerId.orElseThrow())))
                throw new IllegalArgumentException("return readiness must retain all exact selected subjects iff ready");
        }
        static ReturnReadiness unready(String reason, Optional<SubjectId> containerId) {
            return new ReturnReadiness(reason, containerId, Optional.empty(), Optional.empty(), Optional.empty());
        }
        public boolean ready() { return reason.equals("READY"); }
    }

    public static boolean pending(FrontierWorldState state, SubjectId ownerId, PhysicalIntentKind kind) {
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == kind && intent.status() != PhysicalIntentStatus.CONFIRMED
                && intent.lifecycleOwner() == PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE
                && intent.roles().require(PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER).equals(ownerId));
    }
    public static boolean returnedOrLost(FrontierWorldState state, EngineeringWorkOrder owner) {
        return owner.engineeringTeam().stream().flatMap(team -> team.memberIds().stream()).noneMatch(member -> EngineeringToolCustody.holdsTool(state, member))
                && !pending(state, owner.id(), PhysicalIntentKind.EQUIPMENT_RETURN);
    }
    private static FixedPosition fixed(BlockPosition position) {
        return new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z()));
    }
}
