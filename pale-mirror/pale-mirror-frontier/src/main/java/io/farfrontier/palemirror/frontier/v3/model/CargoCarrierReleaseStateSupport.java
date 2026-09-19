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
        if (lease == null || !FrontierSceneBehaviors.isLogistics(lease) || lease.status() != SceneLeaseStatus.HOT || !FrontierSceneBehaviors.logistics(lease).cargoId().equals(released.cargoId())) {
            throw new IllegalArgumentException("cargo carrier release lacks one HOT matching scene lease");
        }
        if (!CargoCarrierIdentity.id(lease).equals(released.carrierId())) throw new IllegalArgumentException("cargo carrier identity is not canonical for its scene");
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || !operation.cargoId().equals(released.cargoId())) {
            throw new IllegalArgumentException("cargo carrier release lacks one en-route operation");
        }
        SupplyContract contract = state.contracts().values().stream().filter(value -> value.cargoId().equals(released.cargoId())).reduce((left, right) -> {
            throw new IllegalArgumentException("cargo has ambiguous supply contracts");
        }).orElseThrow(() -> new IllegalArgumentException("cargo carrier release has no supply contract"));
        if (contract.status() != ContractStatus.LOADED) throw new IllegalArgumentException("only loaded cargo may leave its route carrier");
        ExactInventory inventory = state.inventory().releaseCargoToWorldCarrier(released.cargoId(), released.carrierId());
        // A player/external release interrupts this route before its physical stack can leave
        // the cart. Its former shipment claim cannot remain spendable or fence the ordinary
        // subsequent player/drop handoff, so retire it atomically with that interruption.
        java.util.Set<SubjectId> releasedClaims = inventory.fungibleResources().accounts().values().stream()
                .filter(account -> account.custody().equals(new ResourceCustody.WorldCarrier(released.carrierId())))
                .flatMap(account -> account.claimQuantities().keySet().stream()).collect(java.util.stream.Collectors.toSet());
        if (!releasedClaims.isEmpty()) inventory = inventory.withFungibleResources(inventory.fungibleResources().releaseClaims(releasedClaims));
        Map<SubjectId, SupplyContract> contracts = new LinkedHashMap<>(state.contracts()); contracts.put(contract.id(), contract.withStatus(ContractStatus.INTERRUPTED));
        Map<SubjectId, RouteOperation> operations = new LinkedHashMap<>(state.operations());
        operations.put(operation.id(), new RouteOperation(operation.id(), operation.contractId(), operation.settlementId(), operation.cargoId(), operation.destinationId(),
                operation.unit(), operation.route(), operation.routeIndex(), OperationStage.INTERRUPTED, java.util.Optional.empty(), java.util.Optional.empty(),
                operation.tacticalPlan().withPhase(TacticalPlanPhase.ABORTED)));
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases()); leases.put(lease.id(), lease.withStatus(SceneLeaseStatus.DRAINING));
        StrategicPlanState plans = state.strategicPlans().interruptRouteOperation(operation.id(), operation.settlementId());
        // The carrier has become ordinary world/player-visible custody. Persist that observation
        // before the interruption is visible; a restart may inspect or retain it, never roll it
        // back to the former shipment just because the scene's old chunk is absent.
        FencedRecoveryState recovery = FrontierSceneLeaseStateSupport.observeCargoCarrier(state.fencedRecovery(), lease);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).contracts(contracts).operations(operations)
                .sceneLeases(leases).strategicPlans(plans).fencedRecovery(recovery));
    }
}
