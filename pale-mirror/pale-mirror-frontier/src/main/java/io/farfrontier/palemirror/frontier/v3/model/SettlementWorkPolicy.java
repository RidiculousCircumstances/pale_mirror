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
        return new ResidentWorkPermissions(Map.of(
                ResidentWorkKind.AGRICULTURE, farmers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(id -> id, id -> agriculture.priority())),
                ResidentWorkKind.BAKING, bakers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(id -> id, id -> baking.priority())),
                ResidentWorkKind.LOGISTICS, logistics),
                Map.of(ResidentWorkKind.AGRICULTURE, agriculture.minimumLocalStaff(), ResidentWorkKind.BAKING, baking.minimumLocalStaff()));
    }

    public static ResidentWorkPermissions permissions(FrontierWorldState state,
                                                     io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId) {
        DecisionAuthority owner = state.strategicPlans().requireDecisionAuthority(settlementId);
        if (owner.kind() != DecisionAuthorityKind.SETTLEMENT)
            throw new IllegalArgumentException("resident work permissions need a declared settlement authority");
        return owner.workPermissions();
    }
}
