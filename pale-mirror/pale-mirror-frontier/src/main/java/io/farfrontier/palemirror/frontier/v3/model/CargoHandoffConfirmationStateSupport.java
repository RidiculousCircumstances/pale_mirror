package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Owns exact-cargo confirmation after the generic state transition fence has admitted it. */
final class CargoHandoffConfirmationStateSupport {
    private CargoHandoffConfirmationStateSupport() { }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent current, PhysicalEffectObservation evidence,
                                       Map<PhysicalIntentId, PhysicalIntent> intents, PhysicalIntentId intentId,
                                       PhysicalIntentStatus nextStatus) {
        if (current.kind() != PhysicalIntentKind.CARGO_HANDOFF || !(evidence instanceof CargoHandoffObservation cargo)) {
            throw new IllegalArgumentException("physical intent kind has no matching confirmation evidence");
        }
        RouteOperation operation = state.operations().get(current.causeSubjectId());
        if (operation == null || !operation.cargoId().equals(cargo.cargoId())) {
            throw new IllegalArgumentException("cargo hand-off observation does not match its route operation");
        }
        SubjectId receiver = FrontierCargoValidation.receiverStore(state.bootstrap(), operation);
        if (cargo.placements().stream().anyMatch(placement -> !receiver.equals(placement.receiverSlot().containerId()))) {
            throw new IllegalArgumentException("cargo hand-off observation targets a foreign hive receiver");
        }
        SupplyContract contract = state.contracts().values().stream().filter(value -> value.cargoId().equals(cargo.cargoId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("cargo hand-off has no supply contract"));
        if (contract.status() != ContractStatus.LOADED) throw new IllegalArgumentException("only loaded cargo can complete hand-off");
        Map<SubjectId, SupplyContract> contracts = new LinkedHashMap<>(state.contracts());
        contracts.put(contract.id(), contract.withStatus(ContractStatus.DELIVERED));
        intents.put(intentId, current.withStatus(nextStatus, Optional.of(cargo.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(cargo.id(), cargo);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(),
                state.inventory().completeCargoHandoff(cargo.cargoId(), cargo.placements()), state.productionJobs(), contracts,
                state.operations(), intents, observations, state.sceneLeases(), state.hiveColony(), state.structureDamage(),
                state.physicalDeltas(), state.ambientLeases());
    }
}
