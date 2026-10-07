package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Optional;

/** Deterministic utility policy over retained hive knowledge and exact owned reserves. */
public final class HiveDoctrineProcess {
    private HiveDoctrineProcess() { }

    public static HiveDoctrineState select(FrontierWorldState state, long now) {
        Optional<HiveSettlementKnowledge.Sighting> settlement = state.strategicPlans().hiveSettlementKnowledge().freshest(state.bootstrap().ruleset(), now);
        if (settlement.isPresent()) return new HiveDoctrineState(HiveDoctrine.INTERDICT, now);
        if (hasStoredBiomass(state) || state.strategicPlans().hiveTerritoryKnowledge().freshInfection(state.bootstrap().ruleset(), now).isEmpty()) {
            return new HiveDoctrineState(HiveDoctrine.CONSOLIDATE, now);
        }
        return new HiveDoctrineState(HiveDoctrine.EXPAND, now);
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, HiveDoctrineSelected selected) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive doctrine has a foreign owner");
        HiveDoctrineState prior = state.strategicPlans().hiveDoctrine();
        if (selected.state().selectedAt() < prior.selectedAt()) throw new IllegalArgumentException("hive doctrine cannot move backwards in time");
        return state.withStrategicPlans(state.strategicPlans().withHiveDoctrine(selected.state()));
    }

    private static boolean hasStoredBiomass(FrontierWorldState state) {
        return state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals("minecraft:rotten_flesh")
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && state.isHiveStore(slot.containerId())
                && item.count() > 0 && item.economicOwnerId().equals(state.bootstrap().hive().id())
        ) || state.inventory().fungibleResources().accounts().values().stream().anyMatch(account -> account.custody() instanceof ResourceCustody.Container container
                && state.isHiveStore(container.containerId()) && account.lotQuantities().entrySet().stream().anyMatch(entry -> {
                    ResourceLot lot = state.inventory().fungibleResources().lots().get(entry.getKey());
                    return lot.economicOwnerId().equals(state.bootstrap().hive().id()) && lot.itemKind().equals("minecraft:rotten_flesh") && entry.getValue() > 0;
                }));
    }
}
