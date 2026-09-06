package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
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
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;

import java.util.ArrayList;
import java.util.Comparator;
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
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(action.subject());
        if (assault == null || assault.status() == SettlementAssaultStatus.RESOLVED || !action.id().equals(review(assault, action.dueAt().ticks()).id())) return List.of();
        List<ProposedEvent> events = new ArrayList<>(); issue(state, assault, action.dueAt().ticks()).ifPresent(intent ->
                events.add(new ProposedEvent(assault.settlementId(), new PhysicalIntentPrepared(intent))));
        events.add(new ProposedEvent(assault.id(), new ScheduleEffect.Created(review(assault,
                Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval())))));
        return List.copyOf(events);
    }

    private static java.util.Optional<PhysicalIntent> issue(FrontierWorldState state, SettlementAssault assault, long now) {
        SubjectId depot = FrontierWorldState.depotId(assault.settlementId());
        if (state.inventory().surfaces().get(depot) == null || state.inventory().surfaces().get(depot).status() != ContainerSurfaceStatus.ACTIVE) return java.util.Optional.empty();
        List<SubjectId> candidates = assault.defenderIds().stream().sorted().filter(candidate -> state.inventory().actorItems(candidate).stream()
                        .noneMatch(item -> HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())))
                .filter(candidate -> state.physicalIntents().values().stream().noneMatch(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE
                        && intent.status() != PhysicalIntentStatus.CONFIRMED && intent.subjectIds().contains(candidate))).toList();
        SubjectId resident = candidates.stream().filter(candidate -> !candidate.equals(assault.defenderUnit().leaderId())).findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.getFirst());
        if (resident == null) return java.util.Optional.empty();
        ExactItemStack item = state.inventory().items().values().stream().filter(value -> value.custody() instanceof io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(depot) && value.economicOwnerId().equals(assault.settlementId()))
                .filter(value -> HumanTacticalFunctionProjection.isGrayboxWeaponKind(value.itemKind()))
                .filter(value -> state.physicalIntents().values().stream().noneMatch(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE
                        && intent.status() != PhysicalIntentStatus.CONFIRMED && intent.subjectIds().contains(value.id())))
                .min(Comparator.comparing(ExactItemStack::id)).orElse(null);
        if (item == null) return java.util.Optional.empty();
        String suffix = assault.id().value().substring("assault:".length()) + "-" + resident.value().replace(':', '-') + "-" + item.id().value().replace(':', '-');
        return java.util.Optional.of(new PhysicalIntent(new PhysicalIntentId("intent:equipment-issue-" + suffix), PhysicalIntentKind.EQUIPMENT_ISSUE,
                PhysicalIntentStatus.PREPARED, assault.settlementId(), List.of(assault.id(), resident, item.id()),
                new FixedPosition(FixedScalar.whole(assault.settlementAnchor().x()), FixedScalar.whole(assault.settlementAnchor().y()), FixedScalar.whole(assault.settlementAnchor().z())),
                0, PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED));
    }
}
