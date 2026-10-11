package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Read-only storage admission shared by bakery execution and player-facing explanation. */
public final class ProductionOutputCapacity {
    private ProductionOutputCapacity() { }
    /** Admission only: later execution retains the selected destination in ProductionRights. */
    public static java.util.Optional<ProductionRights> selectDestination(FrontierWorldState state, ProductionRights rights, int quantity, SubjectId source) {
        var home = rights.employer().settlementId();
        var origin = ResourceStorageAccess.at(state, rights.destinationContainerId()).endpoint().station();
        var homePlan = FrontierWorldStateSupport.settlement(state.bootstrap(), home);
        var workshop = homePlan.structures().stream().filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        var departure = SettlementWorkshopServicePort.forWorkshop(workshop).exteriorApproach();
        return ResourceStorageSelection.candidates(state, home, "minecraft:bread", origin, java.util.Set.of(ResourceStorageRole.WAREHOUSE)).stream()
                .filter(endpoint -> ResourceStorageNavigation.reachable(state, departure, endpoint))
                .map(endpoint -> new ProductionRights(rights.mode(), rights.resourceOwner(), endpoint.containerId(), rights.employer()))
                .filter(candidate -> source.equals(candidate.destinationContainerId()) ? canAdmitBreadBatch(state, candidate, quantity)
                        : state.canReceiveFungible(candidate.destinationContainerId(), "minecraft:bread", quantity)).findFirst();
    }

    /** The recipe owner declares its retained inbound demand after input has left storage. */
    public static java.util.List<ContainerInboundCapacity.Demand> pendingInbound(java.util.Map<SubjectId, ProductionJob> jobs) {
        var incoming = new java.util.ArrayList<ContainerInboundCapacity.Demand>();
        for (ProductionJob job : jobs.values()) {
            if (job.bakeryWork().isEmpty()) continue;
            var phase = job.bakeryWork().orElseThrow().phase();
            if (phase != BakeryWorkState.Phase.DELIVERED
                    && (phase != BakeryWorkState.Phase.DEPOT_PICKUP || !BakeryStorage.source(job).equals(BakeryStorage.destination(job))))
                incoming.add(new ContainerInboundCapacity.Demand(job.id(),
                        BakeryStorage.destination(job), job.outputItemKind(), job.outputCount()));
        }
        return java.util.List.copyOf(incoming);
    }

    /** Admission accounts for input replacement and the outputs already committed by other owners. */
    public static boolean canAdmitBreadBatch(FrontierWorldState state, SubjectId settlementId) {
        return canAdmitBreadBatch(state, settlementId, 64);
    }

    public static boolean canAdmitBreadBatch(FrontierWorldState state, SubjectId settlementId, int quantity) {
        return canAdmitBreadBatch(state, ProductionRights.publicService(settlementId), quantity);
    }

    public static boolean canAdmitBreadBatch(FrontierWorldState state, ProductionRights rights, int quantity) {
        Objects.requireNonNull(state, "production output state");
        ProductionStationRecipe.breadOutputQuantity(quantity);
        SubjectId depot = rights.destinationContainerId();
        var storage = state.inventory().containers().get(depot);
        if (storage == null || storage.storage().map(policy -> !policy.filter().accepts("minecraft:bread")).orElse(true)
                || ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return false;
        var pending = state.pendingContainerInbound(depot);
        return state.canReceiveFungible(depot, "minecraft:bread", quantity)
                || state.inventory().canTransformFungible(depot, "minecraft:wheat", quantity,
                    "minecraft:bread", quantity, state.reservedContainerSlots(depot), pending)
                || state.inventory().items().values().stream().anyMatch(item ->
                    item.economicOwnerId().equals(rights.resourceOwner().id()) && item.itemKind().equals("minecraft:wheat")
                    && item.count() == quantity && item.custody() instanceof InventoryCustody.ContainerSlot slot
                    && slot.containerId().equals(depot))
                    && state.inventory().canReserveSlots(depot, state.reservedContainerSlots(depot), pending);
    }

    public static java.util.OptionalInt deliverySlot(FrontierWorldState state, ProductionJob job) {
        if (!job.equals(state.productionJobs().get(job.id())))
            throw new IllegalArgumentException("output capacity names a stale production owner");
        return state.firstFreeContainerSlotForOutput(BakeryStorage.destination(job), job.id());
    }

    public static boolean depotDeliveryUnavailable(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(state, "production output state");
        Objects.requireNonNull(job, "production job");
        if (job.bakeryWork().orElseThrow().phase() != BakeryWorkState.Phase.DEPOT_DELIVERY) return false;
        SubjectId depot = BakeryStorage.destination(job);
        return job.inputHold() instanceof ProductionInputHold.Materialized
                ? deliverySlot(state, job).isEmpty()
                : !state.canReceiveFungibleOutput(depot, job.outputItemKind(), job.outputCount(), job.id());
    }
}
