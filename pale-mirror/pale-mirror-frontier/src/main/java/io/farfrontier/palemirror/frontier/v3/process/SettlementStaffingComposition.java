package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Optional;

/** Composition names family strategies; the shared allocator knows no crop or recipe stages. */
public final class SettlementStaffingComposition {
    public static final SettlementStaffingPolicy POLICY = new SettlementStaffingPolicy(List.of(
            new SettlementStaffingPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.AGRICULTURE; }
                public HumanCapability capability() { return HumanCapability.AGRICULTURE; }
                public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules) {
                    return ResourceSiteHarvestPlanning.staffingDemand(state, home, rules);
                }
            }, new SettlementStaffingPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.BAKING; }
                public HumanCapability capability() { return HumanCapability.INDUSTRY; }
                public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules) {
                    return BakeryJobAdmission.staffingDemand(state, home, rules);
                }
            }, new SettlementStaffingPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.LOGISTICS; }
                public HumanCapability capability() { return HumanCapability.LOGISTICS; }
                public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules) {
                    return GoodsShipmentPlanning.staffingDemand(state, home, rules);
                }
            }));
    private SettlementStaffingComposition() { }
    public static Optional<SettlementWorkPolicyChanged> change(FrontierWorldState state, SubjectId home, long tick) {
        var old = SettlementWorkPolicy.permissions(state, home);
        var next = POLICY.propose(state, home);
        return old.equals(next) ? Optional.empty() : Optional.of(new SettlementWorkPolicyChanged(home,
                state.strategicPlans().requireDecisionAuthority(home).reconsiderationEpoch(), tick, old, next));
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, SettlementWorkPolicyChanged event) {
        var authority = state.strategicPlans().requireDecisionAuthority(event.settlementId());
        if (!subject.equals(event.settlementId()) || authority.kind() != DecisionAuthorityKind.SETTLEMENT
                || authority.reconsiderationEpoch() != event.authorityEpoch()
                || !authority.workPermissions().equals(event.expected())
                || !POLICY.propose(state, subject).equals(event.next()))
            throw new IllegalArgumentException("stale, foreign or forged settlement staffing decision");
        return state.withStrategicPlans(state.strategicPlans().withWorkPermissions(subject, event.next()));
    }
}
