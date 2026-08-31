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
import io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** One bounded exact-tool issue or return for one retained engineering/recovery owner. */
public final class EngineeringEquipmentProcess {
    private EngineeringEquipmentProcess() { }

    public static Optional<PhysicalIntent> issueOne(FrontierWorldState state, RouteConstruction project) {
        if (project.team().isEmpty() || project.status() != RouteConstructionStatus.BUILDING || pending(state, project.id(), PhysicalIntentKind.EQUIPMENT_ISSUE)) {
            return Optional.empty();
        }
        SubjectId depot = FrontierWorldState.depotId(project.settlementId());
        if (state.inventory().surfaces().get(depot) == null || state.inventory().surfaces().get(depot).status() != ContainerSurfaceStatus.ACTIVE) return Optional.empty();
        EngineeringRecoveryTeam team = project.team().orElseThrow();
        SubjectId resident = team.memberIds().stream().filter(member -> !EngineeringToolCustody.holdsTool(state, member)).findFirst().orElse(null);
        if (resident == null || state.actorLocations().get(resident) == null
                || state.actorLocations().get(resident).condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE) return Optional.empty();
        ExactItemStack item = state.inventory().items().values().stream()
                .filter(value -> value.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot))
                .filter(value -> value.economicOwnerId().equals(project.settlementId()) && EngineeringToolCustody.isTool(value.itemKind()))
                .filter(value -> !reservedForIssue(state, value.id()))
                .min(Comparator.comparing(ExactItemStack::id)).orElse(null);
        if (item == null) return Optional.empty();
        String suffix = project.id().value().substring("construction:".length()) + "-" + resident.value().replace(':', '-')
                + "-" + item.id().value().replace(':', '-');
        var anchor = settlement(state, project).anchor();
        return Optional.of(new PhysicalIntent(new PhysicalIntentId("intent:engineering-tool-issue-" + suffix), PhysicalIntentKind.EQUIPMENT_ISSUE,
                PhysicalIntentStatus.PREPARED, project.settlementId(), List.of(project.id(), resident, item.id()),
                new FixedPosition(FixedScalar.whole(anchor.x()), FixedScalar.whole(anchor.y()), FixedScalar.whole(anchor.z())), 0,
                PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED));
    }

    public static Optional<PhysicalIntent> returnOne(FrontierWorldState state, RouteConstruction project) {
        if (project.team().isEmpty() || project.status() != RouteConstructionStatus.READY || pending(state, project.id(), PhysicalIntentKind.EQUIPMENT_RETURN)) {
            return Optional.empty();
        }
        SubjectId depot = FrontierWorldState.depotId(project.settlementId());
        if (state.inventory().surfaces().get(depot) == null || state.inventory().surfaces().get(depot).status() != ContainerSurfaceStatus.ACTIVE) return Optional.empty();
        EngineeringRecoveryTeam team = project.team().orElseThrow();
        ExactItemStack item = team.memberIds().stream().flatMap(member -> state.inventory().actorItems(member).stream())
                .filter(value -> value.economicOwnerId().equals(project.settlementId()) && EngineeringToolCustody.isTool(value.itemKind()))
                .min(Comparator.comparing(ExactItemStack::id)).orElse(null);
        if (item == null || !(item.custody() instanceof InventoryCustody.Actor actor)
                || state.actorLocations().get(actor.actorId()) == null
                || state.actorLocations().get(actor.actorId()).condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE) return Optional.empty();
        int slot = state.inventory().firstFreeSlot(depot).orElse(-1); if (slot < 0) return Optional.empty();
        String suffix = project.id().value().substring("construction:".length()) + "-" + actor.actorId().value().replace(':', '-')
                + "-" + item.id().value().replace(':', '-');
        var anchor = settlement(state, project).anchor();
        return Optional.of(new PhysicalIntent(new PhysicalIntentId("intent:engineering-tool-return-" + suffix), PhysicalIntentKind.EQUIPMENT_RETURN,
                PhysicalIntentStatus.PREPARED, project.settlementId(), List.of(project.id(), actor.actorId(), item.id()),
                new FixedPosition(FixedScalar.whole(anchor.x()), FixedScalar.whole(anchor.y()), FixedScalar.whole(anchor.z())), 0,
                PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED, new PhysicalContainerSlot(depot, slot)));
    }

    public static boolean pending(FrontierWorldState state, SubjectId projectId, PhysicalIntentKind kind) {
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == kind && intent.status() != PhysicalIntentStatus.CONFIRMED
                && !intent.subjectIds().isEmpty() && intent.subjectIds().getFirst().equals(projectId));
    }

    public static boolean returnedOrLost(FrontierWorldState state, RouteConstruction project) {
        return project.team().stream().flatMap(team -> team.memberIds().stream()).noneMatch(member -> EngineeringToolCustody.holdsTool(state, member))
                && !pending(state, project.id(), PhysicalIntentKind.EQUIPMENT_RETURN);
    }

    private static boolean reservedForIssue(FrontierWorldState state, SubjectId itemId) {
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE
                && intent.status() != PhysicalIntentStatus.CONFIRMED && intent.subjectIds().contains(itemId));
    }

    private static io.farfrontier.palemirror.frontier.v3.model.Settlement settlement(FrontierWorldState state, RouteConstruction project) {
        return io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateSupport.settlement(state.bootstrap(), project.settlementId());
    }
}
