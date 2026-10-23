package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
/** Shared station workforce, including retained orders across production families. */
final class ProductionStaffingDemand {
    static SettlementStaffingPort.Demand assess(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long atTick) {
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), home);
        boolean intact = state.structureConditions().get(ProductionProcess.workshop(settlement).id()) == StructureCondition.INTACT;
        boolean input = EquipmentProductionPlanning.ready(state, home, atTick).isPresent() || intact && BakeryBatchSelection.admissibleStock(state, ProductionRights.publicService(home), atTick).isPresent();
        var retained = state.productionJobs().values().stream().filter(job -> job.settlementId().equals(home))
                .map(ProductionJob::workerId).collect(java.util.stream.Collectors.toSet());
        int reserve = intact ? rules.minimumLocalStaff() : 0;
        return new SettlementStaffingPort.Demand(ResidentWorkKind.PRODUCTION, HumanCapability.INDUSTRY,
                input ? rules.targetWorkers() : reserve, reserve, rules.priority(), retained);
    }
    private ProductionStaffingDemand() { }
}
