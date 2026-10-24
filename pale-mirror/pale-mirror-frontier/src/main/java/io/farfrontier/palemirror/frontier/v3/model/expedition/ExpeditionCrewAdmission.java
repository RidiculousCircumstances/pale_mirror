package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Cheap necessary provisioning condition, before geometry discovery. It grants
 * neither a route nor a departure; full journey provisioning remains mandatory. */
public final class ExpeditionCrewAdmission {
    private ExpeditionCrewAdmission() { }

    public static boolean canFeedBeforeTravel(FrontierWorldState state, SubjectId sponsor,
            List<SubjectId> crew, Optional<TransportAsset> asset, long now) {
        return canSupply(state, sponsor, crew, asset, 0, now);
    }
    public static boolean canSupply(FrontierWorldState state, SubjectId sponsor,
            List<SubjectId> crew, Optional<TransportAsset> asset, long duration, long now) {
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
        for (var food : life.foods().foods().values()) {
            long missing = 0;
            for (var id : crew) {
                var resident = state.humanPopulation().resident(id);
                if (resident == null || !resident.settlementId().equals(sponsor))
                    throw new IllegalArgumentException("expedition provisions have a foreign resident");
                int rate = resident.characteristics().effectiveMetabolismPermille(now);
                int satiety = state.humanPopulation().nutrition(id).accrueThrough(now, life, rate).satietyUnits();
                int required = ExpeditionProvisioning.requiredFoodItems(duration, satiety, rate, food, life);
                long personal = UnitInventory.personalAccounts(state, resources, id).stream().filter(a -> a.claimQuantities().isEmpty())
                        .mapToLong(a -> resources.unclaimedQuantity(a.id(), sponsor, food.itemKind())).sum();
                missing += Math.max(0L, required - personal);
            }
            if (missing == 0) return true;
            // Material feasibility needs the eligible stock scope, not a ranked
            // pickup itinerary for every hypothetical crew/load combination.
            long available = ResourceStorageSelection.containers(state, sponsor, food.itemKind(),
                    Set.of(ResourceStorageRole.WAREHOUSE)).stream()
                    .filter(id -> state.inventory().containers().get(id).storage().orElseThrow()
                            .endpoint().settlementId().equals(sponsor))
                    .map(id -> FungibleResourceCustodySupport.accountAtContainer(state, id))
                    .flatMap(Optional::stream).filter(account -> resources.accounts().containsKey(account.id()))
                    .mapToLong(account -> resources.unclaimedQuantity(account.id(), sponsor, food.itemKind())).sum();
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
        var resources = state.inventory().fungibleResources();
        for (var food : life.foods().foods().values().stream().sorted(Comparator.comparing(FoodCatalog.Food::itemKind)).toList()) {
            var missing = new HashMap<SubjectId, Long>();
            for (var id : candidates) {
                var resident = Objects.requireNonNull(state.humanPopulation().resident(id));
                if (!resident.settlementId().equals(sponsor)) throw new IllegalArgumentException("foreign crew candidate");
                int rate = resident.characteristics().effectiveMetabolismPermille(now);
                int satiety = state.humanPopulation().nutrition(id).accrueThrough(now, life, rate).satietyUnits();
                long personal = UnitInventory.personalAccounts(state, resources, id).stream().filter(a -> a.claimQuantities().isEmpty())
                        .mapToLong(a -> resources.unclaimedQuantity(a.id(), sponsor, food.itemKind())).sum();
                missing.put(id, Math.max(0, ExpeditionProvisioning.requiredFoodItems(duration, satiety, rate, food, life) - personal));
            }
            var ranked = candidates.stream().sorted(Comparator.comparingLong(missing::get)).toList();
            var crew = new ArrayList<SubjectId>();
            for (var id : ranked) {
                var proposed = new ArrayList<>(crew); proposed.add(id);
                if (permitted.test(List.copyOf(proposed))) crew.add(id);
                if (crew.size() == count) break;
            }
            if (crew.size() == count && canSupply(state, sponsor, crew, asset, duration, now)) return List.copyOf(crew);
        }
        return List.of();
    }
}
