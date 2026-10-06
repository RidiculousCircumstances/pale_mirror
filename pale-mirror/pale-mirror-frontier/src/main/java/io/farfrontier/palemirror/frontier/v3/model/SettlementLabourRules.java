package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Hashed staffing balance. Capability and executable work semantics belong to each family. */
public record SettlementLabourRules(Map<ResidentWorkKind, Entry> entries) {
    public record Entry(int targetWorkers, int minimumLocalStaff, int priority) {
        public Entry {
            if (targetWorkers < 1 || targetWorkers > ResidentWorkPermissions.MAX_WORKERS_PER_KIND
                    || minimumLocalStaff < 0 || minimumLocalStaff > targetWorkers || priority < 1 || priority > 255)
                throw new IllegalArgumentException("invalid staffing target, reserve or priority");
        }
    }
    public SettlementLabourRules {
        entries = Map.copyOf(entries);
        if (!entries.keySet().equals(EnumSet.allOf(ResidentWorkKind.class)))
            throw new IllegalArgumentException("staffing rules must name every registered work kind");
    }
    public static SettlementLabourRules initial() {
        return new SettlementLabourRules(Map.of(
                ResidentWorkKind.AGRICULTURE, new Entry(3, 1, 1),
                ResidentWorkKind.BAKING, new Entry(2, 1, 1),
                ResidentWorkKind.LOGISTICS, new Entry(64, 0, 4)));
    }
    public String canonicalText() {
        return entries.entrySet().stream().sorted(Comparator.comparingInt(value -> value.getKey().wireTag()))
                .map(value -> value.getKey().wireTag() + ":" + value.getValue().targetWorkers() + ":"
                        + value.getValue().minimumLocalStaff() + ":" + value.getValue().priority())
                .collect(java.util.stream.Collectors.joining(";"));
    }
}
