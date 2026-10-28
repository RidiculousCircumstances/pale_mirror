package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Cheap necessary provisioning condition, before geometry discovery. It grants
 * neither a route nor a departure; full journey provisioning remains mandatory. */
public final class ExpeditionCrewAdmission {
    private record StockScope(SubjectId sponsor, List<SubjectId> residents) {
        private StockScope { residents = List.copyOf(residents); }
    }
    private record FoodStock(Map<SubjectId, Long> personal, long warehouse) {
        private FoodStock { personal = Map.copyOf(personal); }
    }
    private static final ImmutableSnapshotView<StockScope, Map<String, FoodStock>> STOCKS = new ImmutableSnapshotView<>(4, 256);
    private record NeedScope(StockScope people, String food, long duration, long now) { }
    private static final ImmutableSnapshotView<NeedScope, List<SubjectId>> RANKINGS = new ImmutableSnapshotView<>(4, 256);
    private static List<SubjectId> ranked(FrontierWorldState state, SubjectId sponsor, List<SubjectId> candidates,
            FoodCatalog.Food food, long duration, long now, FoodStock stock) {
        return RANKINGS.get(state, new NeedScope(new StockScope(sponsor, candidates), food.itemKind(), duration, now), () -> {
            var life = state.bootstrap().ruleset().residentLife();
            var missing = new HashMap<SubjectId, Long>();
            for (var id : candidates) {
                var resident = Objects.requireNonNull(state.humanPopulation().resident(id));
                if (!resident.settlementId().equals(sponsor)) throw new IllegalArgumentException("foreign crew candidate");
                int rate = resident.characteristics().effectiveMetabolismPermille(now);
                int satiety = state.humanPopulation().nutrition(id).accrueThrough(now, life, rate).satietyUnits();
                missing.put(id, Math.max(0, ExpeditionProvisioning.requiredFoodItems(duration, satiety, rate, food, life)
                        - stock.personal().get(id)));
            }
            return candidates.stream().sorted(Comparator.comparingLong(missing::get)).toList();
        });
    }
    private static Map<String, FoodStock> stocks(FrontierWorldState state, SubjectId sponsor, List<SubjectId> residents) {
        return STOCKS.get(state, new StockScope(sponsor, residents), () -> {
            var resources = state.inventory().fungibleResources(); var result = new HashMap<String, FoodStock>();
            for (var id : residents) {
                var resident = state.humanPopulation().resident(id);
                if (resident == null || !resident.settlementId().equals(sponsor))
                    throw new IllegalArgumentException("expedition provisions have a foreign resident");
            }
            for (var food : state.bootstrap().ruleset().residentLife().foods().foods().values()) {
                var personal = new HashMap<SubjectId, Long>();
                for (var id : residents) personal.put(id, UnitInventory.personalAccounts(state, resources, id).stream()
                        .filter(account -> account.claimQuantities().isEmpty())
                        .mapToLong(account -> resources.unclaimedQuantity(account.id(), sponsor, food.itemKind())).sum());
                long warehouse = ResourceStorageSelection.containers(state, sponsor, food.itemKind(), Set.of(ResourceStorageRole.WAREHOUSE)).stream()
                        .filter(id -> state.inventory().containers().get(id).storage().orElseThrow().endpoint().settlementId().equals(sponsor))
                        .map(id -> FungibleResourceCustodySupport.accountAtContainer(state, id)).flatMap(Optional::stream)
                        .filter(account -> resources.accounts().containsKey(account.id()))
                        .mapToLong(account -> resources.unclaimedQuantity(account.id(), sponsor, food.itemKind())).sum();
                result.put(food.itemKind(), new FoodStock(personal, warehouse));
            }
            return Map.copyOf(result);
        });
    }
    private ExpeditionCrewAdmission() { }

    public static boolean canFeedBeforeTravel(FrontierWorldState state, SubjectId sponsor,
            List<SubjectId> crew, Optional<TransportAsset> asset, long now) {
        return canSupply(state, sponsor, crew, asset, 0, now);
    }
    public static boolean canSupply(FrontierWorldState state, SubjectId sponsor,
            List<SubjectId> crew, Optional<TransportAsset> asset, long duration, long now) {
        return canSupply(state, sponsor, crew, asset, duration, now, null);
    }
    private static boolean canSupply(FrontierWorldState state, SubjectId sponsor,
            List<SubjectId> crew, Optional<TransportAsset> asset, long duration, long now, Map<String, FoodStock> captured) {
        if (duration < 0 || now < 0) throw new IllegalArgumentException("negative provision forecast");
        if (crew.isEmpty() || crew.stream().distinct().count() != crew.size())
            throw new IllegalArgumentException("expedition crew must name distinct residents");
        asset.ifPresent(value -> {
            if (!state.transportFleet().require(value.actorId()).equals(value)
                    || !value.homeSettlementId().equals(sponsor))
                throw new IllegalArgumentException("expedition provisions have a foreign asset");
        });
        var life = state.bootstrap().ruleset().residentLife();
        var resources = state.inventory().fungibleResources();
        var availableStock = captured == null ? stocks(state, sponsor, crew) : captured;
        for (var food : life.foods().foods().values()) {
            long missing = 0;
            for (var id : crew) {
                var resident = state.humanPopulation().resident(id);
                if (resident == null || !resident.settlementId().equals(sponsor))
                    throw new IllegalArgumentException("expedition provisions have a foreign resident");
                int rate = resident.characteristics().effectiveMetabolismPermille(now);
                int satiety = state.humanPopulation().nutrition(id).accrueThrough(now, life, rate).satietyUnits();
                int required = ExpeditionProvisioning.requiredFoodItems(duration, satiety, rate, food, life);
                long personal = availableStock.get(food.itemKind()).personal().get(id);
                missing += Math.max(0L, required - personal);
            }
            if (missing == 0) return true;
            long available = availableStock.get(food.itemKind()).warehouse();
            available += asset.flatMap(value -> FungibleResourceCustodySupport.accountAtContainer(state, value.containerId()))
                    .map(account -> resources.unclaimedQuantity(account.id(), sponsor, food.itemKind())).orElse(0);
            if (available >= missing) return true;
        }
        return false;
    }

    /** Bounded material-aware selection. The caller owns work eligibility and
     * labour commitments; this owner knows only the shared provisioning need. */
    public static List<SubjectId> select(FrontierWorldState state, SubjectId sponsor, List<SubjectId> candidates,
            int count, Optional<TransportAsset> asset, long duration, long now,
            java.util.function.Predicate<List<SubjectId>> permitted) {
        if (count < 1 || duration < 0 || now < 0 || candidates.stream().distinct().count() != candidates.size())
            throw new IllegalArgumentException("invalid expedition candidate set");
        var life = state.bootstrap().ruleset().residentLife();
        // Stock is independent of hypothetical journey duration and nutrition time.
        // Share only immutable quantities; each proposal still computes its own need
        // and asks the caller's current labour-permission predicate.
        var availableStock = stocks(state, sponsor, candidates);
        for (var food : life.foods().foods().values().stream().sorted(Comparator.comparing(FoodCatalog.Food::itemKind)).toList()) {
            // Crew size, asset and labour permission do not change individual food need.
            // Reuse only its exact snapshot/time/duration ordering; permission and the
            // selected crew's actual provisions are still checked on every call.
            var ranked = ranked(state, sponsor, candidates, food, duration, now, availableStock.get(food.itemKind()));
            var crew = new ArrayList<SubjectId>();
            for (var id : ranked) {
                var proposed = new ArrayList<>(crew); proposed.add(id);
                if (permitted.test(List.copyOf(proposed))) crew.add(id);
                if (crew.size() == count) break;
            }
            if (crew.size() == count && canSupply(state, sponsor, crew, asset, duration, now, availableStock)) return List.copyOf(crew);
        }
        return List.of();
    }
}
