package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringRecoveryTeam;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringDepotService;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringJourneyPurpose;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkOrder;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** One bounded exact-tool issue or return for one retained engineering/recovery owner. */
public final class EngineeringEquipmentProcess {
    private EngineeringEquipmentProcess() { }

    public static Optional<PhysicalIntent> issueOne(FrontierWorldState state, EngineeringWorkOrder project) {
        if (project.engineeringTeam().isEmpty() || !project.building() || !EngineeringDepotService.atStations(state, project, EngineeringJourneyPurpose.MUSTER_DEPOT)
                || pending(state, project.id(), PhysicalIntentKind.EQUIPMENT_ISSUE)) {
            return Optional.empty();
        }
        SubjectId depot = FrontierWorldState.depotId(project.settlementId());
        if (state.inventory().surfaces().get(depot) == null || state.inventory().surfaces().get(depot).status() != ContainerSurfaceStatus.ACTIVE) return Optional.empty();
        EngineeringRecoveryTeam team = project.engineeringTeam().orElseThrow();
        SubjectId resident = team.memberIds().stream().filter(member -> !EngineeringToolCustody.holdsTool(state, member)).findFirst().orElse(null);
        if (resident == null || state.actorLocations().get(resident) == null
                || state.actorLocations().get(resident).condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE) return Optional.empty();
        ExactItemStack item = state.inventory().items().values().stream()
                .filter(value -> value.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot))
                .filter(value -> value.economicOwnerId().equals(project.settlementId()) && EngineeringToolCustody.isTool(value.itemKind()))
                .filter(value -> !reservedForIssue(state, value.id()))
                .min(Comparator.comparing(ExactItemStack::id)).orElse(null);
        if (item == null) return Optional.empty();
        String suffix = project.id().value().replace(':', '-') + "-" + resident.value().replace(':', '-')
                + "-" + item.id().value().replace(':', '-');
        var anchor = settlement(state, project).anchor();
        return Optional.of(new PhysicalIntent(new PhysicalIntentId("intent:engineering-tool-issue-" + suffix), PhysicalIntentKind.EQUIPMENT_ISSUE,
                PhysicalIntentStatus.PREPARED, project.settlementId(), List.of(project.id(), resident, item.id()),
                new FixedPosition(FixedScalar.whole(anchor.x()), FixedScalar.whole(anchor.y()), FixedScalar.whole(anchor.z())), 0,
                PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED));
    }

    public static Optional<PhysicalIntent> returnOne(FrontierWorldState state, EngineeringWorkOrder project) {
        ReturnReadiness readiness = returnReadiness(state, project);
        if (!readiness.ready()) return Optional.empty();
        SubjectId actorId = readiness.actorId().orElseThrow();
        ExactItemStack item = state.inventory().items().get(readiness.itemId().orElseThrow());
        PhysicalContainerSlot targetSlot = readiness.targetSlot().orElseThrow();
        String suffix = project.id().value().replace(':', '-') + "-" + actorId.value().replace(':', '-')
                + "-" + item.id().value().replace(':', '-');
        var anchor = settlement(state, project).anchor();
        return Optional.of(new PhysicalIntent(new PhysicalIntentId("intent:engineering-tool-return-" + suffix), PhysicalIntentKind.EQUIPMENT_RETURN,
                PhysicalIntentStatus.PREPARED, project.settlementId(), List.of(project.id(), actorId, item.id()),
                new FixedPosition(FixedScalar.whole(anchor.x()), FixedScalar.whole(anchor.y()), FixedScalar.whole(anchor.z())), 0,
                PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED, targetSlot));
    }

    /**
     * One pure explanation of the terminal tool-return boundary.  This is deliberately shared
     * by the planner and operator diagnostics: a diagnostic must never make up a reason that the
     * next canonical scan would not use.
     */
    public static ReturnReadiness returnReadiness(FrontierWorldState state, EngineeringWorkOrder project) {
        SubjectId depot = FrontierWorldState.depotId(project.settlementId());
        if (project.engineeringTeam().isEmpty()) return ReturnReadiness.unready("NO_ENGINEERING_TEAM", depot);
        if (!project.readyForToolReturn()) return ReturnReadiness.unready("NOT_READY_FOR_RETURN", depot);
        if (pending(state, project.id(), PhysicalIntentKind.EQUIPMENT_RETURN)) return ReturnReadiness.unready("RETURN_INTENT_PENDING", depot);
        if (state.inventory().surfaces().get(depot) == null || state.inventory().surfaces().get(depot).status() != ContainerSurfaceStatus.ACTIVE) {
            return ReturnReadiness.unready("DEPOT_SURFACE_INACTIVE", depot);
        }
        EngineeringRecoveryTeam team = project.engineeringTeam().orElseThrow();
        ExactItemStack item = team.memberIds().stream().flatMap(member -> state.inventory().actorItems(member).stream())
                .filter(value -> value.economicOwnerId().equals(project.settlementId()) && EngineeringToolCustody.isTool(value.itemKind()))
                .min(Comparator.comparing(ExactItemStack::id)).orElse(null);
        if (item == null || !(item.custody() instanceof InventoryCustody.Actor actor)) {
            return ReturnReadiness.unready("NO_EXACT_TEAM_TOOL", depot);
        }
        if (state.actorLocations().get(actor.actorId()) == null
                || state.actorLocations().get(actor.actorId()).condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE) {
            return ReturnReadiness.unready("TOOL_HOLDER_UNAVAILABLE", depot);
        }
        int slot = state.inventory().firstFreeSlot(depot).orElse(-1);
        if (slot < 0) return ReturnReadiness.unready("DEPOT_FULL", depot);
        if (!EngineeringDepotService.atStations(state, project, EngineeringJourneyPurpose.RETURN_DEPOT)) {
            return ReturnReadiness.unready("TOOL_HOLDER_NOT_AT_DEPOT_SERVICE_PORT", depot);
        }
        return new ReturnReadiness("READY", depot, Optional.of(actor.actorId()), Optional.of(item.id()),
                Optional.of(new PhysicalContainerSlot(depot, slot)));
    }

    /** Bounded, immutable facts explaining whether one exact return intent can be prepared. */
    public record ReturnReadiness(String reason, SubjectId depotId, Optional<SubjectId> actorId,
                                  Optional<SubjectId> itemId, Optional<PhysicalContainerSlot> targetSlot) {
        public ReturnReadiness {
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("return readiness reason is required");
            java.util.Objects.requireNonNull(depotId, "return readiness depot");
            actorId = Optional.ofNullable(actorId).orElseThrow(() -> new IllegalArgumentException("return readiness actor optional is required"));
            itemId = Optional.ofNullable(itemId).orElseThrow(() -> new IllegalArgumentException("return readiness item optional is required"));
            targetSlot = Optional.ofNullable(targetSlot).orElseThrow(() -> new IllegalArgumentException("return readiness slot optional is required"));
            if (reason.equals("READY") != (actorId.isPresent() && itemId.isPresent() && targetSlot.isPresent())) {
                throw new IllegalArgumentException("return readiness must retain all exact intent subjects iff ready");
            }
        }

        static ReturnReadiness unready(String reason, SubjectId depotId) {
            return new ReturnReadiness(reason, depotId, Optional.empty(), Optional.empty(), Optional.empty());
        }

        public boolean ready() { return reason.equals("READY"); }
    }

    public static boolean pending(FrontierWorldState state, SubjectId projectId, PhysicalIntentKind kind) {
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == kind && intent.status() != PhysicalIntentStatus.CONFIRMED
                && !intent.subjectIds().isEmpty() && intent.subjectIds().getFirst().equals(projectId));
    }

    public static boolean returnedOrLost(FrontierWorldState state, EngineeringWorkOrder project) {
        return project.engineeringTeam().stream().flatMap(team -> team.memberIds().stream()).noneMatch(member -> EngineeringToolCustody.holdsTool(state, member))
                && !pending(state, project.id(), PhysicalIntentKind.EQUIPMENT_RETURN);
    }

    private static boolean reservedForIssue(FrontierWorldState state, SubjectId itemId) {
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE
                && intent.status() != PhysicalIntentStatus.CONFIRMED && intent.subjectIds().contains(itemId));
    }

    private static io.farfrontier.palemirror.frontier.v3.model.Settlement settlement(FrontierWorldState state, EngineeringWorkOrder project) {
        return io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateSupport.settlement(state.bootstrap(), project.settlementId());
    }
}
