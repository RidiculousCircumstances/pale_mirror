package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Atomic canonical consequence of exposing a HOT cargo carrier to ordinary Minecraft custody. */
final class CargoCarrierReleaseStateSupport {
    private CargoCarrierReleaseStateSupport() { }

    static FrontierWorldState release(FrontierWorldState state, CargoCarrierReleased released) {
        Objects.requireNonNull(state, "world state"); Objects.requireNonNull(released, "cargo carrier release");
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        if (lease == null || !(lease.cause() instanceof LogisticsSceneCause) || lease.status() != SceneLeaseStatus.HOT || !lease.cargoId().equals(released.cargoId())) {
            throw new IllegalArgumentException("cargo carrier release lacks one HOT matching scene lease");
        }
        if (!CargoCarrierIdentity.id(lease).equals(released.carrierId())) throw new IllegalArgumentException("cargo carrier identity is not canonical for its scene");
        RouteOperation operation = state.operations().get(lease.operationId());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || !operation.cargoId().equals(released.cargoId())) {
            throw new IllegalArgumentException("cargo carrier release lacks one en-route operation");
        }
        SupplyContract contract = state.contracts().values().stream().filter(value -> value.cargoId().equals(released.cargoId())).reduce((left, right) -> {
            throw new IllegalArgumentException("cargo has ambiguous supply contracts");
        }).orElseThrow(() -> new IllegalArgumentException("cargo carrier release has no supply contract"));
        if (contract.status() != ContractStatus.LOADED) throw new IllegalArgumentException("only loaded cargo may leave its route carrier");
        ExactInventory inventory = state.inventory().releaseCargoToWorldCarrier(released.cargoId(), released.carrierId());
        Map<SubjectId, SupplyContract> contracts = new LinkedHashMap<>(state.contracts()); contracts.put(contract.id(), contract.withStatus(ContractStatus.INTERRUPTED));
        Map<SubjectId, RouteOperation> operations = new LinkedHashMap<>(state.operations());
        operations.put(operation.id(), new RouteOperation(operation.id(), operation.settlementId(), operation.cargoId(), operation.destinationId(),
                operation.participantIds(), operation.route(), operation.routeIndex(), OperationStage.INTERRUPTED));
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases()); leases.put(lease.id(), lease.withStatus(SceneLeaseStatus.DRAINING));
        StrategicPlanState plans = state.strategicPlans().interruptRouteOperation(operation.id(), operation.settlementId());
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), inventory, state.productionJobs(), contracts, operations,
                state.logisticsHistory(), state.physicalIntents(), state.physicalObservations(), leases, state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases(),
                state.routeConstructions(), state.routeTopology(), plans, state.humanPopulation(), state.companies(), state.resourceSites());
    }
}
