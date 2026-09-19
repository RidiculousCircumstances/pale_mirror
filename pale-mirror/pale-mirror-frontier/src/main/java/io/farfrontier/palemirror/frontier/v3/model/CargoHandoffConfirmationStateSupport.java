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
public final class CargoHandoffConfirmationStateSupport {
    private CargoHandoffConfirmationStateSupport() { }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent current, PhysicalEffectObservation evidence,
                                       Map<PhysicalIntentId, PhysicalIntent> intents, PhysicalIntentId intentId,
                                       PhysicalIntentStatus nextStatus) {
        if (current.kind() != PhysicalIntentKind.CARGO_HANDOFF) {
            throw new IllegalArgumentException("physical intent is not a cargo hand-off");
        }
        if (evidence instanceof CargoHandoffObservation cargo) return completeExact(state, current, cargo, intents, intentId, nextStatus);
        if (evidence instanceof FungibleCargoHandoffObservation cargo) return completeFungible(state, current, cargo, intents, intentId, nextStatus);
        throw new IllegalArgumentException("cargo hand-off requires exact or fungible cargo observation evidence");
    }

    private static FrontierWorldState completeExact(FrontierWorldState state, PhysicalIntent current, CargoHandoffObservation cargo,
                                                     Map<PhysicalIntentId, PhysicalIntent> intents, PhysicalIntentId intentId,
                                                     PhysicalIntentStatus nextStatus) {
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

    private static FrontierWorldState completeFungible(FrontierWorldState state, PhysicalIntent current, FungibleCargoHandoffObservation cargo,
                                                        Map<PhysicalIntentId, PhysicalIntent> intents, PhysicalIntentId intentId,
                                                        PhysicalIntentStatus nextStatus) {
        RouteOperation operation = state.operations().get(current.causeSubjectId());
        if (operation == null || !operation.cargoId().equals(cargo.cargoId())) {
            throw new IllegalArgumentException("fungible cargo hand-off observation does not match its route operation");
        }
        SubjectId receiver = FrontierCargoValidation.receiverStore(state.bootstrap(), operation);
        if (cargo.stacks().stream().anyMatch(stack -> !(stack.address() instanceof PhysicalStackAddress.ContainerSlot slot)
                || !slot.slot().containerId().equals(receiver))) {
            throw new IllegalArgumentException("fungible cargo hand-off observation targets a foreign hive receiver");
        }
        SupplyContract contract = state.contracts().values().stream().filter(value -> value.cargoId().equals(cargo.cargoId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("fungible cargo hand-off has no supply contract"));
        if (contract.status() != ContractStatus.LOADED || !state.inventory().cargo().get(cargo.cargoId()).fungibleContents()) {
            throw new IllegalArgumentException("only loaded fungible cargo can complete hand-off");
        }
        Map<SubjectId, SupplyContract> contracts = new LinkedHashMap<>(state.contracts());
        contracts.put(contract.id(), contract.withStatus(ContractStatus.DELIVERED));
        intents.put(intentId, current.withStatus(nextStatus, Optional.of(cargo.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(cargo.id(), cargo);
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(),
                state.inventory().completeObservedFungibleCargoHandoff(cargo.cargoId(), receiver, cargo.authorityEpoch(), cargo.stacks()),
                state.productionJobs(), contracts, state.operations(), intents, observations, state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }
}
