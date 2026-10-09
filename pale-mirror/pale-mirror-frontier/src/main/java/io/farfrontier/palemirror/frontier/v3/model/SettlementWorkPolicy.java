package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashSet;
import java.util.Map;

/** Fresh-settlement staffing producer. Selection here is initialization, never an availability fallback. */
public final class SettlementWorkPolicy {
    private SettlementWorkPolicy() { }

    public static ResidentWorkPermissions initial(Settlement settlement) {
        return initial(settlement, SettlementLabourRules.initial());
    }
    public static ResidentWorkPermissions initial(Settlement settlement, SettlementLabourRules rules) {
        return initial(settlement, rules, rules.entries().containsKey(ResidentWorkKind.EXTRACTION));
    }
    public static ResidentWorkPermissions initial(Settlement settlement, SettlementLabourRules rules, boolean extractionSite) {
        var agriculture = rules.entries().get(ResidentWorkKind.AGRICULTURE);
        var baking = rules.entries().get(ResidentWorkKind.BAKING);
        var hauling = rules.entries().get(ResidentWorkKind.LOGISTICS);
        var farmers = new LinkedHashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        var bakers = new LinkedHashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        for (Resident resident : settlement.residents()) {
            if (resident.role() == ResidentRole.FARMER && farmers.size() < agriculture.targetWorkers()) farmers.add(resident.id());
            if (resident.role() == ResidentRole.CRAFTER && bakers.size() < baking.targetWorkers()) bakers.add(resident.id());
        }
        if (farmers.size() < agriculture.minimumLocalStaff() || bakers.size() < baking.minimumLocalStaff())
            throw new IllegalArgumentException("initial settlement cannot satisfy configured local staffing reserves");
        var logistics = settlement.residents().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Resident::id, resident -> hauling.priority()));
        var priorities = new java.util.EnumMap<ResidentWorkKind, Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Integer>>(ResidentWorkKind.class);
        priorities.putAll(Map.of(
                ResidentWorkKind.AGRICULTURE, farmers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(id -> id, id -> agriculture.priority())),
                ResidentWorkKind.BAKING, bakers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(id -> id, id -> baking.priority())),
                ResidentWorkKind.LOGISTICS, logistics));
        var reserves = new java.util.EnumMap<ResidentWorkKind, Integer>(ResidentWorkKind.class);
        reserves.put(ResidentWorkKind.AGRICULTURE, agriculture.minimumLocalStaff());
        reserves.put(ResidentWorkKind.BAKING, baking.minimumLocalStaff());
        var extraction = rules.entries().get(ResidentWorkKind.EXTRACTION);
        if (extractionSite) {
            if (extraction == null) throw new IllegalArgumentException("extraction staffing has no declared content rules");
            var miners = settlement.residents().stream().filter(resident -> !farmers.contains(resident.id()) && !bakers.contains(resident.id()))
                    .sorted(java.util.Comparator.comparing(Resident::id)).limit(extraction.targetWorkers())
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(Resident::id, resident -> extraction.priority()));
            if (miners.size() < extraction.minimumLocalStaff())
                throw new IllegalArgumentException("initial settlement cannot satisfy extraction staffing reserve");
            priorities.put(ResidentWorkKind.EXTRACTION, miners);
            reserves.put(ResidentWorkKind.EXTRACTION, extraction.minimumLocalStaff());
        }
        return new ResidentWorkPermissions(priorities, reserves);
    }

    public static ResidentWorkPermissions permissions(FrontierWorldState state,
                                                     io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId) {
        DecisionAuthority owner = state.strategicPlans().requireDecisionAuthority(settlementId);
        if (owner.kind() != DecisionAuthorityKind.SETTLEMENT)
            throw new IllegalArgumentException("resident work permissions need a declared settlement authority");
        return owner.workPermissions();
    }
}
