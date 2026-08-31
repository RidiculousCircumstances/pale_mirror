package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentReturnStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Bounded settlement-owned review that returns one exact released defender stack at a time. */
public final class DefenderEquipmentReturnProcess {
    public static final String REVIEW_ACTION = "frontier.population.defender_equipment_return.review";
    private DefenderEquipmentReturnProcess() { }

    public static ScheduledAction review(SettlementAssault assault, long dueAt) {
        String suffix = assault.id().value().substring("assault:".length()) + "-at-" + dueAt;
        return new ScheduledAction(new ScheduleId("schedule:defender-equipment-return-review-" + suffix), new SimInstant(dueAt), 0,
                assault.id(), REVIEW_ACTION, 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(action.subject());
        if (assault == null || !action.id().equals(review(assault, action.dueAt().ticks()).id())) return List.of();
        Optional<PhysicalIntent> intent = returnOne(state, assault);
        List<ProposedEvent> events = new ArrayList<>();
        intent.ifPresent(value -> events.add(new ProposedEvent(assault.settlementId(), new PhysicalIntentPrepared(value))));
        if (assault.status() != SettlementAssaultStatus.RESOLVED || hasPendingOrReturnable(state, assault)) {
            events.add(new ProposedEvent(assault.id(), new ScheduleEffect.Created(review(assault,
                    Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval())))));
        }
        return List.copyOf(events);
    }

    private static Optional<PhysicalIntent> returnOne(FrontierWorldState state, SettlementAssault assault) {
        if (assault.status() != SettlementAssaultStatus.RESOLVED) return Optional.empty();
        SubjectId depot = FrontierWorldState.depotId(assault.settlementId());
        if (state.inventory().surfaces().get(depot) == null || state.inventory().surfaces().get(depot).status() != ContainerSurfaceStatus.ACTIVE) return Optional.empty();
        if (state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN
                && intent.status() != PhysicalIntentStatus.CONFIRMED && intent.subjectIds().getFirst().equals(assault.id()))) return Optional.empty();
        ExactItemStack item = assault.defenderIds().stream().sorted().flatMap(resident -> state.inventory().actorItems(resident).stream())
                .filter(value -> value.economicOwnerId().equals(assault.settlementId()))
                .filter(value -> HumanTacticalFunctionProjection.isGrayboxWeaponKind(value.itemKind()))
                .min(Comparator.comparing(ExactItemStack::id)).orElse(null);
        if (item == null || !(item.custody() instanceof InventoryCustody.Actor actor)) return Optional.empty();
        int slot = state.inventory().firstFreeSlot(depot).orElse(-1); if (slot < 0) return Optional.empty();
        String suffix = assault.id().value().substring("assault:".length()) + "-" + actor.actorId().value().replace(':', '-')
                + "-" + item.id().value().replace(':', '-');
        return Optional.of(new PhysicalIntent(new PhysicalIntentId("intent:equipment-return-" + suffix), PhysicalIntentKind.EQUIPMENT_RETURN,
                PhysicalIntentStatus.PREPARED, assault.settlementId(), List.of(assault.id(), actor.actorId(), item.id()),
                new FixedPosition(FixedScalar.whole(assault.settlementAnchor().x()), FixedScalar.whole(assault.settlementAnchor().y()),
                        FixedScalar.whole(assault.settlementAnchor().z())), 0, PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED,
                new PhysicalContainerSlot(depot, slot)));
    }

    private static boolean hasPendingOrReturnable(FrontierWorldState state, SettlementAssault assault) {
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN
                        && intent.status() != PhysicalIntentStatus.CONFIRMED && intent.subjectIds().getFirst().equals(assault.id()))
                || assault.defenderIds().stream().flatMap(resident -> state.inventory().actorItems(resident).stream())
                .anyMatch(item -> item.economicOwnerId().equals(assault.settlementId())
                        && HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind()));
    }
}
