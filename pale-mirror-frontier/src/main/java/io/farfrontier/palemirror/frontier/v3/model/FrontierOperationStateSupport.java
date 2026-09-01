package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

import java.util.LinkedHashMap;
import java.util.Objects;

/** State mutation contract for failure of an exact en-route operation. */
final class FrontierOperationStateSupport {
    private FrontierOperationStateSupport() { }

    static FrontierWorldState fail(FrontierWorldState state, SubjectId operationId) {
        RouteOperation current = state.operations().get(Objects.requireNonNull(operationId, "operation id"));
        if (current == null || current.stage() != OperationStage.EN_ROUTE) throw new IllegalArgumentException("only an en-route operation can fail");
        if (state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).anyMatch(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operationId) && lease.status() != SceneLeaseStatus.CLOSED
                && !(lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART && lease.recoveryEvidence().isPresent()))) {
            throw new IllegalArgumentException("operation cannot fail before its active scene lease closes");
        }
        var operations = new LinkedHashMap<>(state.operations());
        operations.put(operationId, new RouteOperation(current.id(), current.settlementId(), current.cargoId(), current.destinationId(),
                current.unit(), current.route(), current.routeIndex(), OperationStage.FAILED, java.util.Optional.empty(), java.util.Optional.empty()));
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), operations,
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }

    /** Read-only terminal-pair validation shared by compaction admission and execution. */
    static TerminalLogisticsReceipt terminalLogisticsReceipt(FrontierWorldState state, SubjectId operationId, long terminalAtTick) {
        RouteOperation operation = state.operations().get(Objects.requireNonNull(operationId, "terminal operation id"));
        if (operation == null) throw new IllegalArgumentException("terminal logistics operation is absent");
        SupplyContract contract = state.contracts().values().stream().filter(value -> value.cargoId().equals(operation.cargoId())).reduce((left, right) -> {
            throw new IllegalArgumentException("terminal logistics cargo has ambiguous contracts");
        }).orElseThrow(() -> new IllegalArgumentException("terminal logistics operation has no contract"));
        if (state.inventory().cargo().containsKey(operation.cargoId())) throw new IllegalArgumentException("terminal logistics cargo remains claimed");
        if (state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).anyMatch(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id()))
                || state.strategicPlans().routeEngagements().values().stream().anyMatch(engagement -> engagement.operationId().equals(operation.id()))
                || state.strategicPlans().tasks().values().stream().anyMatch(task -> task.operationTarget().equals(java.util.Optional.of(operation.id())))) {
            throw new IllegalArgumentException("terminal logistics operation retains a dependent scene or strategic claim");
        }
        if (state.physicalIntents().values().stream().anyMatch(intent -> intent.status() != PhysicalIntentStatus.CONFIRMED
                && (intent.causeSubjectId().equals(operation.id()) || intent.causeSubjectId().equals(contract.id())
                || intent.subjectIds().contains(operation.id()) || intent.subjectIds().contains(contract.id()) || intent.subjectIds().contains(operation.cargoId())))) {
            throw new IllegalArgumentException("terminal logistics operation retains an unresolved physical intent");
        }
        return new TerminalLogisticsReceipt(operation.id(), contract.id(), operation.cargoId(), operation.settlementId(), operation.destinationId(),
                operation.participantIds(), terminalOutcome(operation, contract), terminalAtTick);
    }

    private static TerminalLogisticsReceipt.TerminalLogisticsOutcome terminalOutcome(RouteOperation operation, SupplyContract contract) {
        if (operation.stage() == OperationStage.COMPLETED && contract.status() == ContractStatus.DELIVERED) return TerminalLogisticsReceipt.TerminalLogisticsOutcome.DELIVERED;
        if (operation.stage() == OperationStage.INTERRUPTED && contract.status() == ContractStatus.INTERRUPTED) return TerminalLogisticsReceipt.TerminalLogisticsOutcome.INTERRUPTED;
        if (operation.stage() == OperationStage.FAILED && contract.status() == ContractStatus.INTERRUPTED) return TerminalLogisticsReceipt.TerminalLogisticsOutcome.FAILED;
        throw new IllegalArgumentException("operation and contract are not a compactable terminal pair");
    }
}
