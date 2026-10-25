package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentRequirements;
import java.util.*;

/** Necessary availability is cheap and never starts navigation or another economic plan. */
final class ForestryWorkPlanning {
    private ForestryWorkPlanning() { }
    static boolean demanded(FrontierWorldState state, SubjectId home) {
        return state.bootstrap().ruleset().forestry().harvests().stream().anyMatch(profile ->
                ForestryStockDemand.requested(state, home, profile.outputItem(), Optional.empty()));
    }
    private static boolean knownSource(FrontierWorldState state, SubjectId home) {
        return io.farfrontier.palemirror.frontier.v3.model.forestry.ForestKnowledge.harvestable(state, home);
    }
    static SettlementStaffingPort.Demand staffingDemand(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long tick) {
        boolean requested = demanded(state, home) && knownSource(state, home);
        var retained = state.forest().work().values().stream().filter(job -> job.home().equals(home))
                .map(job -> job.execution().actorId()).collect(java.util.stream.Collectors.toSet());
        return new SettlementStaffingPort.Demand(ResidentWorkKind.FORESTRY, HumanCapability.EXTRACTION,
                requested ? rules.targetWorkers() : 0, requested ? rules.minimumLocalStaff() : 0, rules.priority(), retained);
    }
    static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long tick) {
        if (!demanded(state, resident.settlementId()) || !knownSource(state, resident.settlementId())) return false;
        return WorkEquipmentSelection.hasAvailable(state, resident.settlementId(), EquipmentRequirements.allOf(
                Set.of(state.bootstrap().ruleset().forestry().tool())), WorkEquipmentClaimComposition.reserved(state));
    }
}
