package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Optional;

/** Composition names family strategies; the shared allocator knows no crop or recipe stages. */
public final class SettlementStaffingComposition {
    public static final SettlementStaffingPolicy POLICY = new SettlementStaffingPolicy(List.of(
            new SettlementStaffingPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.MEDICAL; }
                public HumanCapability capability() { return HumanCapability.MEDICINE; }
                public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long atTick) {
                    var clinical=state.humanPopulation().clinical();
                    var retained=clinical.tasks().values().stream().filter(t->t.home().equals(home)&&t.role()==io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalTask.Role.MEDIC)
                        .map(io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalTask::actor).collect(java.util.stream.Collectors.toSet());
                    boolean patients=clinical.wounds().keySet().stream().anyMatch(actor->state.humanPopulation().resident(actor).settlementId().equals(home));
                    return new Demand(kind(),capability(),patients?rules.targetWorkers():0,patients?rules.minimumLocalStaff():0,rules.priority(),retained);
                }
            },new SettlementStaffingPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.GUARD; }
                public HumanCapability capability() { return HumanCapability.SECURITY; }
                public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long atTick) {
                    return GuardWorkProcess.staffingDemand(state, home, rules);
                }
            }, new SettlementStaffingPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.AGRICULTURE; }
                public HumanCapability capability() { return HumanCapability.AGRICULTURE; }
                public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long atTick) {
                    return ResourceSiteHarvestPlanning.staffingDemand(state, home, rules, atTick);
                }
            }, new SettlementStaffingPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.PRODUCTION; }
                public HumanCapability capability() { return HumanCapability.INDUSTRY; }
                public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long atTick) {
                    return ProductionStaffingDemand.assess(state, home, rules, atTick);
                }
            }, new SettlementStaffingPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.LOGISTICS; }
                public HumanCapability capability() { return HumanCapability.LOGISTICS; }
                public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long atTick) {
                    return GoodsShipmentPlanning.staffingDemand(state, home, rules, atTick);
                }
            }, new SettlementStaffingPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.EXTRACTION; }
                public HumanCapability capability() { return HumanCapability.EXTRACTION; }
                public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long atTick) {
                    return ExtractionWorkPlanning.staffingDemand(state, home, rules, atTick);
                }
            }, new SettlementStaffingPort() {
                public ResidentWorkKind kind() { return ResidentWorkKind.FORESTRY; }
                public HumanCapability capability() { return HumanCapability.EXTRACTION; }
                public Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long atTick) {
                    return ForestryWorkPlanning.staffingDemand(state, home, rules, atTick);
                }
            }));
    private SettlementStaffingComposition() { }
    public static Optional<SettlementWorkPolicyChanged> change(FrontierWorldState state, SubjectId home, long tick) {
        var old = SettlementWorkPolicy.permissions(state, home);
        var next = POLICY.propose(state, home, tick);
        return old.equals(next) ? Optional.empty() : Optional.of(new SettlementWorkPolicyChanged(home,
                state.strategicPlans().requireDecisionAuthority(home).reconsiderationEpoch(), tick, old, next));
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, SettlementWorkPolicyChanged event) {
        var authority = state.strategicPlans().requireDecisionAuthority(event.settlementId());
        if (!subject.equals(event.settlementId()) || authority.kind() != DecisionAuthorityKind.SETTLEMENT
                || authority.reconsiderationEpoch() != event.authorityEpoch()
                || !authority.workPermissions().equals(event.expected())
                || !POLICY.propose(state, subject, event.assessedAtTick()).equals(event.next()))
            throw new IllegalArgumentException("stale, foreign or forged settlement staffing decision");
        return state.withStrategicPlans(state.strategicPlans().withWorkPermissions(subject, event.next()));
    }
}
