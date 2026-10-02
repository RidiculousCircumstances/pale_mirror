package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Comparator;
import java.util.Map;

/** Explicit settlement priorities, independent of concrete planner implementations. */
public record SettlementManagementPolicy(Map<SettlementOperationPlanner.Priority, Integer> priorities) {
    public SettlementManagementPolicy {
        priorities = Map.copyOf(priorities);
        if (priorities.size() != SettlementOperationPlanner.Priority.values().length)
            throw new IllegalArgumentException("settlement policy must rank every registered priority");
    }
    public static SettlementManagementPolicy standard() {
        return new SettlementManagementPolicy(Map.of(SettlementOperationPlanner.Priority.CRITICAL, 400,
                SettlementOperationPlanner.Priority.IMPORTANT, 300, SettlementOperationPlanner.Priority.NORMAL, 200,
                SettlementOperationPlanner.Priority.BACKGROUND, 100));
    }
    public Comparator<SettlementOperationPlanner.Offer> ordering() {
        return Comparator.<SettlementOperationPlanner.Offer>comparingInt(offer -> priorities.get(offer.priority())).reversed()
                .thenComparing(SettlementOperationPlanner.Offer::proposal, StrategicOperationProposal.HIGHEST_UTILITY);
    }
}
