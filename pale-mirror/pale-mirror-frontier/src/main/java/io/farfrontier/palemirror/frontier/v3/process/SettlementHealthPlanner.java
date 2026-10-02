package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Comparator;
import java.util.Optional;

/** Profile owner proposes operations; it never commits goals or takes a resident body. */
final class SettlementHealthPlanner implements SettlementOperationPlanner {
    @Override public String id() { return "frontier:health"; }
    @Override public Assessment assess(FrontierWorldState state, Settlement settlement) {
        Optional<SettlementStructure> infirmary = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .filter(structure -> state.structureConditions().get(structure.id()) != StructureCondition.DESTROYED).min(Comparator.comparing(SettlementStructure::id));
        if (infirmary.isPresent()) {
            Optional<StrategicOperationProposal> containment = state.strategicPlans().infectionKnowledge().known(settlement.id()).values().stream()
                    .map(known -> new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, Optional.of(known.cell()), known.intensity().value().raw()))
                    .sorted(StrategicOperationProposal.HIGHEST_UTILITY).findFirst();
            if (containment.isPresent()) return Assessment.offer(settlement.id(), containment.orElseThrow(), Priority.IMPORTANT);
        }
        return Assessment.empty();
    }
}
