package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** One order's future pickups and confirmed station loads. The current hand remains in the job's input hold. */
public record StationWorkMaterials(List<Pickup> queued, List<Loaded> loaded, long tripOrdinal) {
    public record Pickup(String itemKind, SubjectId sourceContainer, SubjectId sourceAccount,
                         SubjectId claimId, Map<SubjectId, Integer> lots, boolean withdrawn) {
        public Pickup {
            ProductionRecipe.requireItemKey(itemKind); Objects.requireNonNull(sourceContainer);
            Objects.requireNonNull(sourceAccount); Objects.requireNonNull(claimId); lots = portions(lots);
        }
        public Pickup(String kind, SubjectId container, SubjectId account, SubjectId claim, Map<SubjectId, Integer> lots) {
            this(kind, container, account, claim, lots, false);
        }
        public int quantity() { return lots.values().stream().mapToInt(Integer::intValue).sum(); }
        public FungibleResourceCustodySupport.LotSelection selection() {
            return new FungibleResourceCustodySupport.LotSelection(sourceAccount, lots);
        }
    }
    public record Loaded(String itemKind, SubjectId claimId, Map<SubjectId, Integer> lots) {
        public Loaded { ProductionRecipe.requireItemKey(itemKind); Objects.requireNonNull(claimId); lots = portions(lots); }
        public int quantity() { return lots.values().stream().mapToInt(Integer::intValue).sum(); }
    }
    public StationWorkMaterials {
        queued = List.copyOf(queued); loaded = List.copyOf(loaded);
        if (tripOrdinal < 0 || queued.size() + loaded.size() > 64
                || java.util.stream.Stream.concat(queued.stream().map(Pickup::claimId), loaded.stream().map(Loaded::claimId)).distinct().count()
                    != queued.size() + loaded.size())
            throw new IllegalArgumentException("station inputs repeat a claim or exceed their bounded workflow");
    }
    public static StationWorkMaterials empty() { return new StationWorkMaterials(List.of(), List.of(), 0); }
    public Map<SubjectId, Integer> loadedLots() {
        var lots = new TreeMap<SubjectId, Integer>();
        loaded.forEach(input -> input.lots().forEach((id, quantity) -> lots.merge(id, quantity, Math::addExact)));
        return Map.copyOf(lots);
    }
    public Map<SubjectId, Integer> loadedClaims() {
        var claims = new TreeMap<SubjectId, Integer>(); loaded.forEach(input -> claims.put(input.claimId(), input.quantity()));
        return Map.copyOf(claims);
    }
    public StationWorkMaterials deposited(Loaded current) {
        var next = new ArrayList<>(loaded); next.add(current);
        return new StationWorkMaterials(queued, next, tripOrdinal);
    }
    public StationWorkMaterials nextPickup() {
        if (queued.isEmpty()) throw new IllegalArgumentException("station has no next material pickup");
        return new StationWorkMaterials(queued.subList(1, queued.size()), loaded, Math.addExact(tripOrdinal, 1));
    }
    public StationWorkMaterials lost(SubjectId claim, SubjectId stationContainer, SubjectId stationAccount) {
        var nextQueued = new ArrayList<Pickup>(); boolean found = false;
        for (var pickup : queued) {
            boolean match = pickup.claimId().equals(claim); found |= match;
            nextQueued.add(match ? new Pickup(pickup.itemKind(), pickup.sourceContainer(), pickup.sourceAccount(), pickup.claimId(), pickup.lots(), true) : pickup);
        }
        var nextLoaded = new ArrayList<Loaded>();
        for (var input : loaded) {
            if (!input.claimId().equals(claim)) nextLoaded.add(input);
            else {
                found = true;
                nextQueued.add(new Pickup(input.itemKind(), stationContainer, stationAccount, input.claimId(), input.lots(), true));
            }
        }
        if (!found) throw new IllegalArgumentException("station loss has no retained material claim");
        return new StationWorkMaterials(nextQueued, nextLoaded, tripOrdinal);
    }
    private static Map<SubjectId, Integer> portions(Map<SubjectId, Integer> values) {
        values = Map.copyOf(values);
        if (values.isEmpty() || values.size() > 64 || values.values().stream().anyMatch(quantity -> quantity < 1)
                || values.values().stream().mapToLong(Integer::longValue).sum() > 64)
            throw new IllegalArgumentException("station material leg needs its exact bounded lot portions");
        return values;
    }
}
