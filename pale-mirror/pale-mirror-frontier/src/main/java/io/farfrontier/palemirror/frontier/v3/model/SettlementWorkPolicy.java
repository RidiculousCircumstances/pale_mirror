package io.farfrontier.palemirror.frontier.v3.model;

import java.util.LinkedHashSet;
import java.util.Map;

/** Fresh-settlement staffing producer. Selection here is initialization, never an availability fallback. */
public final class SettlementWorkPolicy {
    private SettlementWorkPolicy() { }

    public static ResidentWorkPermissions initial(Settlement settlement) {
        var farmers = new LinkedHashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        var bakers = new LinkedHashSet<io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        for (Resident resident : settlement.residents()) {
            if (resident.role() == ResidentRole.FARMER && farmers.size() < 3) farmers.add(resident.id());
            if (resident.role() == ResidentRole.CRAFTER && bakers.size() < 2) bakers.add(resident.id());
        }
        if (farmers.size() != 3 || bakers.size() != 2)
            throw new IllegalArgumentException("initial settlement needs three exact farmers and two exact bakers");
        return new ResidentWorkPermissions(Map.of(ResidentWorkKind.AGRICULTURE, farmers, ResidentWorkKind.BAKING, bakers));
    }

    public static ResidentWorkPermissions permissions(FrontierWorldState state,
                                                     io.farfrontier.palemirror.frontier.v3.api.SubjectId settlementId) {
        DecisionAuthority owner = state.strategicPlans().requireDecisionAuthority(settlementId);
        if (owner.kind() != DecisionAuthorityKind.SETTLEMENT)
            throw new IllegalArgumentException("resident work permissions need a declared settlement authority");
        return owner.workPermissions();
    }
}
