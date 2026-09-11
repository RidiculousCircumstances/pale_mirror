package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Owns F0.3 fungible confirmation reductions without growing the aggregate world-state owner. */
final class FungiblePhysicalIntentConfirmationStateSupport {
    private FungiblePhysicalIntentConfirmationStateSupport() { }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent current, PhysicalEffectObservation evidence,
                                       Map<PhysicalIntentId, PhysicalIntent> intents, PhysicalIntentId intentId,
                                       PhysicalIntentStatus nextStatus) {
        if (current.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION && evidence instanceof FungibleResourceConsumedObservation consumed) {
            if (!state.hiveColony().growthJobs().containsKey(current.causeSubjectId())) {
                throw new IllegalArgumentException("fungible consumption has no supported owning process");
            }
            intents.put(intentId, current.withStatus(nextStatus, Optional.of(consumed.id())));
            Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
            observations.put(consumed.id(), consumed);
            HiveGrowthStateSupport.FungibleConsumption consumedState = HiveGrowthStateSupport.consumeObservedFungible(
                    state, current.causeSubjectId(), consumed);
            return state.next(state.actorLocations(), state.structureConditions(), state.infection(), consumedState.inventory(),
                    state.productionJobs(), state.contracts(), state.operations(), intents, observations, state.sceneLeases(),
                    consumedState.colony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
        }
        if (current.kind() != PhysicalIntentKind.CARGO_HANDOFF || !(evidence instanceof FungibleCargoHandoffObservation cargo)) {
            return null;
        }
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
