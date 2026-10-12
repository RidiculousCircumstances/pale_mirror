package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Assault owner closes its selected return before stock departure; generic custody knows no assaults. */
final class DefenderEquipmentSourceDeparture {
    private DefenderEquipmentSourceDeparture() { }
    private static List<SettlementAssault> visits(FrontierWorldState state, SubjectId item) {
        return state.strategicPlans().settlementAssaults().values().stream()
                .filter(assault -> assault.equipmentReturnVisit().filter(visit -> visit.itemId().equals(item)).isPresent())
                .filter(assault -> {
                    var intent = state.physicalIntents().get(assault.equipmentReturnVisit().orElseThrow().transferId());
                    return intent == null || intent.status() != PhysicalIntentStatus.CONFIRMED;
                })
                .sorted(java.util.Comparator.comparing(SettlementAssault::id)).toList();
    }
    static boolean committed(FrontierWorldState state, SubjectId item) {
        return EquipmentItemDeparture.committed(state, item, PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT) || !visits(state, item).isEmpty();
    }
    static List<ProposedEvent> plan(FrontierWorldState state, FrontierCommand command, SubjectId item, ProposedEvent observation) {
        var events = new ArrayList<>(EquipmentItemDeparture.plan(state, command, item, observation, PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT));
        events.remove(events.size() - 1);
        for (var assault : visits(state, item)) events.add(new ProposedEvent(assault.id(), new DefenderEquipmentReturnVisitChanged(
                assault.id(), assault.equipmentReturnVisit(), Optional.empty())));
        events.add(observation);
        return List.copyOf(events);
    }
    static FrontierWorldState observe(FrontierWorldState state, SubjectId item, InventoryCustody source, ExactInventory inventory) {
        if (!visits(state, item).isEmpty()) throw new IllegalArgumentException("defender equipment source departure omitted its exact selected-visit retirement");
        return EquipmentItemDeparture.observe(state, item, source, inventory, PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
    }
}
