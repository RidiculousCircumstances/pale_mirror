package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Read-only storage admission shared by bakery execution and player-facing explanation. */
public final class ProductionOutputCapacity {
    private ProductionOutputCapacity() { }
    /** Admission only: later execution retains the selected destination in ProductionRights. */
    public static java.util.Optional<ProductionRights> selectDestination(FrontierWorldState state, ProductionRights rights, int quantity, SubjectId source) {
        return selectDestination(state, rights, quantity, source, true);
    }
    /** Stock-policy assessment only; never proves access or authorizes production. */
    public static java.util.Optional<ProductionRights> stockDestination(FrontierWorldState state, ProductionRights rights, int quantity, SubjectId source) {
        return selectDestination(state, rights, quantity, source, false);
    }
    private static java.util.Optional<ProductionRights> selectDestination(FrontierWorldState state, ProductionRights rights, int quantity,
            SubjectId source, boolean requireAccess) {
        var home = rights.employer().settlementId();
        var homePlan = FrontierWorldStateSupport.settlement(state.bootstrap(), home);
        var workshop = homePlan.structures().stream().filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        var departure = SettlementWorkshopServicePort.forWorkshop(workshop).exteriorApproach();
        for (var endpoint : ResourceStorageSelection.candidates(state, home, "minecraft:bread", departure, java.util.Set.of(ResourceStorageRole.WAREHOUSE))) {
            var candidate = new ProductionRights(rights.mode(), rights.resourceOwner(), endpoint.containerId(), rights.employer());
            if (!canAdmitBatch(state, candidate, quantity, source)) continue;
            if (requireAccess) try { ResourceStorageNavigation.plannedPath(state, departure, endpoint); }
            catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH) throw unavailable;
                continue;
            }
            return java.util.Optional.of(candidate);
        }
        return java.util.Optional.empty();
    }
    /** Input can free output capacity only in its own selected source storage. */
    public static boolean canAdmitBatch(FrontierWorldState state, ProductionRights rights, int quantity, SubjectId source) {
        return source.equals(rights.destinationContainerId()) ? canAdmitBreadBatch(state, rights, quantity)
                : state.canReceiveFungible(rights.destinationContainerId(), "minecraft:bread", BakeryRecipes.bread(state).outputForInput(quantity));
    }

    /** Generic station result admission; its producer supplies the exact recipe and departure. */
    public static java.util.Optional<ProductionRights> selectRecipeDestination(FrontierWorldState state, ProductionRights rights,
            ProductionRecipe recipe, int inputQuantity, SubjectId source, SurfaceAnchor departure,
            java.util.Optional<StationWorkState.MaintenanceTarget> maintenance) {
        for (var endpoint : ResourceStorageSelection.candidates(state, rights.employer().settlementId(), recipe.product().itemKind(),
                departure, java.util.Set.of(ResourceStorageRole.WAREHOUSE))) {
            var candidate = new ProductionRights(rights.mode(), rights.resourceOwner(), endpoint.containerId(), rights.employer());
            if (!canAdmitRecipe(state, candidate, recipe, inputQuantity, source, maintenance)) continue;
            return java.util.Optional.of(candidate); // Admission navigation separately proves all selected endpoints together.
        }
        return java.util.Optional.empty();
    }
    public static boolean canAdmitRecipe(FrontierWorldState state, ProductionRights rights,
            ProductionRecipe recipe, int inputQuantity, SubjectId source,
            java.util.Optional<StationWorkState.MaintenanceTarget> maintenance) {
        var record = state.inventory().containers().get(rights.destinationContainerId());
        if (record == null || record.storage().map(storage -> !storage.filter().accepts(recipe.product().itemKind())).orElse(true)
                || ReferenceContainerCustody.blocksCanonicalUse(state, record.id())) return false;
        if (recipe.outcome() instanceof ProductionRecipeOutcome.Repair && maintenance.filter(target -> {
            var item = state.inventory().items().get(target.itemId());
            return item != null && item.economicOwnerId().equals(rights.resourceOwner().id())
                    && item.itemKind().equals(recipe.product().itemKind())
                    && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(record.id());
        }).isPresent()) return state.inventory().canReserveSlots(record.id(), state.reservedContainerSlots(record.id()),
                state.pendingContainerInbound(record.id()));
        if (!(recipe.outcome() instanceof ProductionRecipeOutcome.Fungible))
            return state.firstFreeContainerSlot(record.id()).isPresent();
        int output = recipe.outputForInput(inputQuantity);
        return state.canReceiveFungible(record.id(), recipe.product().itemKind(), output)
                || source.equals(record.id()) && recipe.ingredient().acceptedItems().stream().anyMatch(kind ->
                    state.inventory().canTransformFungible(record.id(), kind, inputQuantity, recipe.product().itemKind(), output,
                            state.reservedContainerSlots(record.id()), state.pendingContainerInbound(record.id())))
                || source.equals(record.id()) && state.inventory().items().values().stream().anyMatch(item ->
                    item.economicOwnerId().equals(rights.resourceOwner().id()) && recipe.ingredient().accepts(item.itemKind())
                    && item.count() == inputQuantity && item.custody() instanceof InventoryCustody.ContainerSlot slot
                    && slot.containerId().equals(record.id()))
                    && state.inventory().canReserveSlots(record.id(), state.reservedContainerSlots(record.id()), state.pendingContainerInbound(record.id()));
    }

    /** The recipe owner declares its retained inbound demand after input has left storage. */
    public static java.util.List<ContainerInboundCapacity.Demand> pendingInbound(java.util.Map<SubjectId, ProductionJob> jobs) {
        var incoming = new java.util.ArrayList<ContainerInboundCapacity.Demand>();
        for (ProductionJob job : jobs.values()) {
            if (job.stationWork().isEmpty()) continue;
            var phase = job.stationWork().orElseThrow().phase();
            boolean retainedTargetOccupiesDestination = phase == StationWorkState.Phase.TARGET_PICKUP
                    && job.stationWork().orElseThrow().maintenanceTarget().orElseThrow().sourceContainerId().equals(StationWorkStorage.destination(job));
            boolean fungibleInputReplacesOutput = inboundRepresentation(job) == ContainerInboundCapacity.Representation.FUNGIBLE
                    && phase == StationWorkState.Phase.DEPOT_PICKUP && StationWorkStorage.source(job).equals(StationWorkStorage.destination(job));
            if (phase != StationWorkState.Phase.DELIVERED && !retainedTargetOccupiesDestination && !fungibleInputReplacesOutput)
                incoming.add(new ContainerInboundCapacity.Demand(job.id(),
                        StationWorkStorage.destination(job), job.outputItemKind(), job.outputCount(), inboundRepresentation(job)));
        }
        return java.util.List.copyOf(incoming);
    }

    static ContainerInboundCapacity.Representation inboundRepresentation(ProductionJob job) {
        return job.stationWork().orElseThrow().outputRepresentation() == StationWorkProduct.Representation.EXACT_ITEM
                ? ContainerInboundCapacity.Representation.INDIVIDUAL : ContainerInboundCapacity.Representation.FUNGIBLE;
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
        int output = BakeryRecipes.bread(state).outputForInput(quantity);
        SubjectId depot = rights.destinationContainerId();
        var storage = state.inventory().containers().get(depot);
        if (storage == null || storage.storage().map(policy -> !policy.filter().accepts("minecraft:bread")).orElse(true)
                || ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return false;
        var pending = state.pendingContainerInbound(depot);
        return state.canReceiveFungible(depot, "minecraft:bread", output)
                || state.inventory().canTransformFungible(depot, "minecraft:wheat", quantity,
                    "minecraft:bread", output, state.reservedContainerSlots(depot), pending)
                || state.inventory().items().values().stream().anyMatch(item ->
                    item.economicOwnerId().equals(rights.resourceOwner().id()) && item.itemKind().equals("minecraft:wheat")
                    && item.count() == quantity && item.custody() instanceof InventoryCustody.ContainerSlot slot
                    && slot.containerId().equals(depot))
                    && state.inventory().canReserveSlots(depot, state.reservedContainerSlots(depot), pending);
    }

    public static java.util.OptionalInt deliverySlot(FrontierWorldState state, ProductionJob job) {
        if (!job.equals(state.productionJobs().get(job.id())))
            throw new IllegalArgumentException("output capacity names a stale production owner");
        return state.firstFreeContainerSlotForOutput(StationWorkStorage.destination(job), job.id());
    }

    public static boolean depotDeliveryUnavailable(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(state, "production output state");
        Objects.requireNonNull(job, "production job");
        if (job.stationWork().orElseThrow().phase() != StationWorkState.Phase.DEPOT_DELIVERY) return false;
        SubjectId depot = StationWorkStorage.destination(job);
        return StationWorkProduct.exact(state, job)
                ? deliverySlot(state, job).isEmpty()
                : !state.canReceiveFungibleOutput(depot, job.outputItemKind(), job.outputCount(), job.id());
    }
}
