package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Institution-to-machine admission. No worker, movement, physical write or private inventory ledger. */
public final class AutomaticStationProduction {
    public record Offer(ProductionOperation operation, ClaimAllocation claim, long epoch) { }
    public static List<ContainerRecord> stations(FrontierWorldState state, SubjectId home, ProductionRecipe recipe) {
        return state.inventory().containers().values().stream().filter(record -> record.ownerId().equals(home))
                .filter(record -> record.productionStation().filter(spec -> spec.capability() == recipe.capability()).isPresent())
                .filter(record -> state.inventory().surfaces().get(record.id()).status() != ContainerSurfaceStatus.RETIRED
                        && state.inventory().surfaces().get(record.id()).status() != ContainerSurfaceStatus.CONFLICT)
                .filter(record -> state.structureConditions().get(record.productionStation().orElseThrow().facilityId()) == StructureCondition.INTACT)
                .sorted(Comparator.comparing(ContainerRecord::id)).toList();
    }
    public static Optional<ProductionCatalog.AutomaticPolicy> policy(FrontierWorldState state, ProductionStationSpec spec) {
        return state.bootstrap().ruleset().production().automaticPolicies().stream()
                .filter(value -> state.bootstrap().ruleset().production().require(value.recipeId()).capability() == spec.capability())
                .reduce((a, b) -> { throw new IllegalArgumentException("station requires an explicit single production policy"); });
    }
    public static Optional<Offer> offer(FrontierWorldState state, SubjectId stationId, long tick) {
        var record = ProductionStationEnergy.record(state, stationId); var spec = record.productionStation().orElseThrow();
        var policy = policy(state, spec).orElse(null);
        if (policy == null || !stations(state, record.ownerId(), state.bootstrap().ruleset().production().require(policy.recipeId())).contains(record)
                || ReferenceContainerCustody.blocksCanonicalUse(state, record.id())
                || ContainerPhysicalAuthorityComposition.pending(state, record.id())) return Optional.empty();
        var machine = state.productionStations().require(stationId);
        if (machine.operation().isPresent() || machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent()) return Optional.empty();
        var recipe = state.bootstrap().ruleset().production().require(policy.recipeId());
        var account = FungibleResourceCustodySupport.accountAtContainer(state, record.id()).orElse(null);
        if (account == null) return Optional.empty();
        var ledger = state.inventory().fungibleResources();
        if (!ProductionStationRecipe.portVacant(state, spec, ActorContainerItemOrder.StationPort.OUTPUT)
                || recipe.ingredient().acceptedItems().stream().noneMatch(kind -> ledger.unclaimedQuantity(
                        account.id(), record.ownerId(), kind) >= recipe.ingredient().quantity())
                || machine.storedHeat() < recipe.heatPerBatch()) return Optional.empty();
        long missing = ProductionStockDemand.read(state, record.ownerId(), recipe.product().itemKind(), tick).missing();
        if (missing < recipe.product().quantity()) return Optional.empty();
        for (var kind : recipe.ingredient().acceptedItems().stream().sorted().toList()) {
            int limit = Math.toIntExact(Math.min(policy.maximumBatches(), missing / recipe.product().quantity())) * recipe.ingredient().quantity();
            int quantity = ProductionBatchSizing.inputQuantity(recipe,
                    Math.min(limit, ledger.unclaimedQuantity(account.id(), record.ownerId(), kind)), 64, machine.storedHeat());
            if (quantity == 0 || !ProductionStationProcessing.canStart(state, stationId, recipe.id(), quantity)) continue;
            var selected = FungibleResourceCustodySupport.selectAtContainer(state, record.id(), record.ownerId(), kind, quantity);
            if (selected.isEmpty() || !FungibleResourceCustodySupport.canReserve(state, selected.orElseThrow())) continue;
            long epoch = ReferenceContainerCustody.hasLiveCustody(state, record.id())
                    ? ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id()))
                        .mapToLong(PhysicalStackBinding::authorityEpoch).findFirst().orElseThrow() : 0;
            String token = UUID.nameUUIDFromBytes((stationId.value() + "/" + machine.revision()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            var id = new SubjectId("operation:automatic-" + token); var claimId = new SubjectId("claim:automatic-" + token);
            var lots = selected.orElseThrow().lotQuantities();
            var claim = new ClaimAllocation(claimId, id, record.ownerId(), kind, quantity, lots, ClaimPurpose.STATION_PROCESSING);
            var operation = new ProductionOperation(new ProductionOperation.Id(id), stationId, id, recipe.id(),
                    new ProductionOperation.FungibleInput(account.id(), lots, Map.of(claimId, quantity)),
                    new SubjectId("lot:automatic-output-" + token), quantity, recipe.outputForInput(quantity),
                    Math.multiplyExact(recipe.heatPerBatch(), quantity / recipe.ingredient().quantity()), Optional.empty(), false,
                    new ProductionOperation.SettlementInitiator(record.ownerId()));
            return Optional.of(new Offer(operation, claim, epoch));
        }
        return Optional.empty();
    }
    public static FrontierWorldState start(FrontierWorldState state, SubjectId subject, ProductionStationAutomaticStarted event) {
        var machine = state.productionStations().require(subject);
        if (!subject.equals(event.stationId()) || machine.revision() != event.machineRevision())
            throw new IllegalArgumentException("automatic station admission has stale identity or revision");
        var offer = offer(state, subject, event.atTick()).orElseThrow(() -> new IllegalArgumentException("automatic station has no executable demand"));
        if (!offer.operation().recipeId().equals(event.recipeId())) throw new IllegalArgumentException("automatic station recipe differs from mandate");
        var spec = ProductionStationEnergy.record(state, subject).productionStation().orElseThrow();
        var input = (ProductionOperation.FungibleInput) offer.operation().inputCustody();
        var resources = state.inventory().fungibleResources();
        resources = offer.epoch() == 0 ? resources.reserve(offer.claim(), input.accountId())
                : resources.reserveBound(offer.claim(), input.accountId(), offer.epoch());
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                .productionStations(ProductionStationProcessing.start(state, spec, offer.operation())));
    }
    private AutomaticStationProduction() { }
}
