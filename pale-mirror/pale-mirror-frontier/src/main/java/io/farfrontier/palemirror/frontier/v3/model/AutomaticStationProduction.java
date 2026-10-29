package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Institution-to-machine admission. No worker, movement, physical write or private inventory ledger. */
public final class AutomaticStationProduction {
    public record Offer(ProductionOperation operation, List<ClaimAllocation> claims, long epoch) { }
    private record StationKey(SubjectId home, ProductionStationSpec.Capability capability) { }
    private static final ImmutableKeyedInputView<StationKey, List<ContainerRecord>> INSTALLED = new ImmutableKeyedInputView<>(512);
    public static List<ContainerRecord> stations(FrontierWorldState state, SubjectId home, ProductionRecipe recipe) {
        var installed = InstalledStationFacts.forRecipe(state, home, recipe);
        return INSTALLED.getPublished(state.bootstrap(), new StationKey(home, recipe.capability()),
                List.of(installed, state.inventory().containers()), () -> installed.stream()
                        .map(value -> state.inventory().containers().get(value.container())).toList());
    }
    public static Optional<ProductionCatalog.AutomaticPolicy> policy(FrontierWorldState state, ProductionStationSpec spec) {
        return state.bootstrap().ruleset().production().automaticPolicies().stream()
                .filter(value -> state.bootstrap().ruleset().production().require(value.recipeId()).capability() == spec.capability())
                .reduce((a, b) -> { throw new IllegalArgumentException("station requires an explicit single production policy"); });
    }
    private record Candidate(ContainerRecord record, ProductionCatalog.AutomaticPolicy policy,
                             ProductionRecipe recipe, CustodyAccount account) { }

    private static Optional<Candidate> candidate(FrontierWorldState state, SubjectId stationId) {
        var record = ProductionStationEnergy.record(state, stationId); var spec = record.productionStation().orElseThrow();
        var policy = policy(state, spec).orElse(null);
        if (policy == null || !stations(state, record.ownerId(), state.bootstrap().ruleset().production().require(policy.recipeId())).contains(record)
                || ReferenceContainerCustody.blocksCanonicalUse(state, record.id())
                || ContainerPhysicalAuthorityComposition.pending(state, record.id())) return Optional.empty();
        var machine = state.productionStations().require(stationId);
        if (machine.operation().isPresent() || machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent()) return Optional.empty();
        var recipe = state.bootstrap().ruleset().production().require(policy.recipeId());
        var account = FungibleResourceCustodySupport.accountAtContainer(state, record.id(), ContainerSectionId.INPUT).orElse(null);
        if (account == null) return Optional.empty();
        var ledger = state.inventory().fungibleResources();
        if (!ProductionStationRecipe.portVacant(state, spec, ActorContainerItemOrder.StationPort.OUTPUT)) return Optional.empty();
        return Optional.of(new Candidate(record, policy, recipe, account));
    }
    public static Optional<Offer> offer(FrontierWorldState state, SubjectId stationId, long tick) {
        var candidate = candidate(state, stationId).orElse(null);
        if (candidate == null) return Optional.empty();
        var record = candidate.record(); var policy = candidate.policy(); var recipe = candidate.recipe();
        var account = candidate.account(); var machine = state.productionStations().require(stationId);
        var ledger = state.inventory().fungibleResources();
        long missing = ProductionStockDemand.read(state, record.ownerId(), recipe.product().itemKind(), tick).missing();
        int maximum = ProductionBatchSizing.batchesForDemand(recipe, missing, policy.maximumBatches());
        for (int batches = maximum; batches > 0; batches--) {
            var selected = ProductionMaterialSupply.atAccount(ledger, account, record.ownerId(), recipe, batches);
            if (selected.isEmpty() || selected.orElseThrow().sources().stream().anyMatch(source -> !FungibleResourceCustodySupport.canReserve(state, source.selection()))) continue;
            var bundle = selected.orElseThrow();
            long epoch = ReferenceContainerCustody.hasLiveCustody(state, record.id())
                    ? state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(record.id())).authorityEpoch() : 0;
            String token = UUID.nameUUIDFromBytes((stationId.value() + "/" + machine.revision()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            var id = new SubjectId("operation:automatic-" + token);
            var lots = new TreeMap<SubjectId, Integer>(); var claims = new ArrayList<ClaimAllocation>(); var claimed = new TreeMap<SubjectId, Integer>();
            for (var source : bundle.sources()) {
                var claimId = new SubjectId("claim:automatic-" + UUID.nameUUIDFromBytes((token + "/" + source.itemKind()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                source.selection().lotQuantities().forEach((lot, quantity) -> lots.merge(lot, quantity, Math::addExact));
                var claim = new ClaimAllocation(claimId, id, record.ownerId(), source.itemKind(), source.selection().quantity(), source.selection().lotQuantities(), ClaimPurpose.STATION_PROCESSING);
                claims.add(claim); claimed.put(claimId, source.selection().quantity());
            }
            var operation = new ProductionOperation(new ProductionOperation.Id(id), stationId, id, recipe.id(),
                    new ProductionOperation.FungibleInput(account.id(), lots, claimed), new SubjectId("lot:automatic-output-" + token),
                    bundle.quantity(), recipe.outputForInput(bundle.quantity()), Optional.empty(), false,
                    new ProductionOperation.SettlementInitiator(record.ownerId()), Optional.empty());
            return Optional.of(new Offer(operation, List.copyOf(claims), epoch));
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
        for (var claim : offer.claims()) resources = offer.epoch() == 0 ? resources.reserve(claim, input.accountId())
                : resources.reserveBound(claim, input.accountId(), offer.epoch());
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                .productionStations(ProductionStationProcessing.start(state, spec, offer.operation())));
    }
    private AutomaticStationProduction() { }
}
