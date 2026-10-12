package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;
import io.farfrontier.palemirror.frontier.v3.model.ResourceStorageSelection;
import io.farfrontier.palemirror.frontier.v3.model.ResourceStorageRole;
import io.farfrontier.palemirror.frontier.v3.model.ResourceStorageAccess;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ContainerPhysicalAuthorityComposition;
import io.farfrontier.palemirror.frontier.v3.model.StableCorrelationIdentity;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentServiceBoundary;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentPhysicalAuthority;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;

import java.util.ArrayList;
import java.util.List;

/** Bounded settlement-owned review that admits at most one exact defender issue per assault step. */
public final class DefenderEquipmentProcess {
    public static final String REVIEW_ACTION = "frontier.population.defender_equipment.review";
    private DefenderEquipmentProcess() { }

    public static ScheduledAction review(SettlementAssault assault, long dueAt) {
        String suffix = assault.id().value().substring("assault:".length()) + "-at-" + dueAt;
        return new ScheduledAction(new ScheduleId("schedule:defender-equipment-review-" + suffix), new SimInstant(dueAt), 0, assault.id(), REVIEW_ACTION, 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        if (!REVIEW_ACTION.equals(action.kind())) throw new IllegalArgumentException("equipment issue rejects a foreign scheduled kind");
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(action.subject());
        if (assault == null || assault.status() == SettlementAssaultStatus.RESOLVED || !action.id().equals(review(assault, action.dueAt().ticks()).id()))
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        List<ProposedEvent> events = new ArrayList<>(); issue(state, assault, action.dueAt().ticks()).ifPresent(intent ->
                events.add(new ProposedEvent(assault.settlementId(), new PhysicalIntentPrepared(intent))));
        events.add(new ProposedEvent(assault.id(), new ScheduleEffect.Created(review(assault,
                Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval())))));
        return List.copyOf(events);
    }

    private static java.util.Optional<PhysicalIntent> issue(FrontierWorldState state, SettlementAssault assault, long now) {
        List<SubjectId> candidates = assault.defenderIds().stream().sorted().filter(candidate -> state.inventory().actorItems(candidate).stream()
                        .noneMatch(item -> HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())))
                .filter(candidate -> state.actorLocations().get(candidate).condition().status() == ActorLifeStatus.ALIVE
                        && !EquipmentPhysicalAuthority.actorCommitted(state, candidate)).toList();
        SubjectId resident = candidates.stream().filter(candidate -> !candidate.equals(assault.defenderUnit().leaderId())).findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.getFirst());
        if (resident == null) return java.util.Optional.empty();
        var origin = state.actorLocations().get(resident).supportingSurface();
        ExactItemStack item = HumanTacticalFunctionProjection.grayboxWeaponKinds().stream().sorted()
                .flatMap(kind -> ResourceStorageSelection.exactSources(state, assault.settlementId(), kind, 1, origin,
                        java.util.Set.of(ResourceStorageRole.WAREHOUSE)).stream())
                .filter(value -> {
                    var source = ((InventoryCustody.ContainerSlot) value.custody()).containerId();
                    return !io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody.blocksCanonicalUse(state, source)
                            // This unfinished family has no admitted service journey. Never reserve remote stock.
                            && ResourceStorageAccess.at(state, source).stations().contains(origin)
                            && !ContainerPhysicalAuthorityComposition.pending(state, source);
                })
                .filter(value -> state.physicalIntents().values().stream().noneMatch(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE
                        && intent.status() != PhysicalIntentStatus.CONFIRMED && intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT).equals(value.id())))
                .findFirst().orElse(null);
        if (item == null) return java.util.Optional.empty();
        SubjectId container = ((InventoryCustody.ContainerSlot) item.custody()).containerId();
        ResourceStorageAccess.at(state, container);
        var body = state.actorLocations().get(resident).body();
        String suffix = StableCorrelationIdentity.digest(assault.id().value() + "|" + resident.value() + "|"
                + item.id().value() + "|" + container.value() + "|" + now);
        var intent = new PhysicalIntent(new PhysicalIntentId("intent:equipment-issue-" + suffix), PhysicalIntentKind.EQUIPMENT_ISSUE,
                PhysicalIntentStatus.PREPARED, assault.settlementId(), PhysicalIntentRoleBinding.assaultEquipmentIssue(assault.id(), resident, item.id(), container),
                new FixedPosition(FixedScalar.whole(body.x()), FixedScalar.whole(body.y()), FixedScalar.whole(body.z())),
                0, PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED,
                new io.farfrontier.palemirror.frontier.v3.model.EquipmentServiceRequest(
                        new io.farfrontier.palemirror.frontier.v3.model.ServiceAccessDemand.Identity(
                                io.farfrontier.palemirror.frontier.v3.model.ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT,
                                assault.id(), resident, container), now),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
        EquipmentServiceBoundary.validateIntent(state, intent);
        return java.util.Optional.of(intent);
    }
}
