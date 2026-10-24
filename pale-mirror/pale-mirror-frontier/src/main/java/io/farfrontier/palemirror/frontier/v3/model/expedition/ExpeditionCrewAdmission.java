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
                int required = ExpeditionProvisioning.requiredFoodItems(0, satiety, rate, food, life);
                long personal = UnitInventory.accounts(resources, id).stream().filter(a -> a.claimQuantities().isEmpty())
                        .mapToLong(a -> resources.unclaimedQuantity(a.id(), sponsor, food.itemKind())).sum();
                missing += Math.max(0L, required - personal);
            }
            if (missing == 0) return true;
            var origin = state.actorLocations().get(crew.getFirst()).supportingSurface();
            long available = ResourceStorageSelection.sources(state, resources, sponsor, food.itemKind(), origin,
                    Set.of(ResourceStorageRole.WAREHOUSE)).stream()
                    .filter(source -> source.endpoint().settlementId().equals(sponsor))
                    .mapToLong(ResourceStorageSelection.Source::quantity).sum();
            available += asset.flatMap(value -> FungibleResourceCustodySupport.accountAtContainer(state, value.containerId()))
                    .map(account -> resources.unclaimedQuantity(account.id(), sponsor, food.itemKind())).orElse(0);
            if (available >= missing) return true;
        }
        return false;
    }
}
