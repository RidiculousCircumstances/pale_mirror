package io.farfrontier.palemirror.internal.world;

import java.util.EnumMap;
import java.util.Map;

import io.farfrontier.palemirror.domain.MigrantGroupState;
import io.farfrontier.palemirror.domain.MigrantGroupStatus;
import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.LivingRegionStatus;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.ResourceStock;
import io.farfrontier.palemirror.domain.RouteState;
import io.farfrontier.palemirror.domain.RouteStatus;
import io.farfrontier.palemirror.domain.SettlementState;
import io.farfrontier.palemirror.domain.SettlementStatus;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Schema-v17 persistence for coarse regional simulation state. */
final class RegionalStateCodec {
    private RegionalStateCodec() { }

    static void write(CompoundTag tag, WorldState state) {
        ListTag settlements = new ListTag();
        state.settlements().forEach(value -> settlements.add(writeSettlement(value)));
        tag.put("settlements", settlements);
        ListTag routes = new ListTag();
        state.routes().forEach(value -> routes.add(writeRoute(value)));
        tag.put("routes", routes);
        ListTag migrants = new ListTag();
        state.migrantGroups().forEach(value -> migrants.add(writeMigrantGroup(value)));
        tag.put("migrantGroups", migrants);
        ListTag regions = new ListTag();
        state.livingRegions().forEach(value -> regions.add(writeRegion(value)));
        tag.put("livingRegions", regions);
    }

    static void read(CompoundTag tag, WorldState state) {
        for (Tag element : tag.getList("settlements", Tag.TAG_COMPOUND)) {
            state.putSettlement(readSettlement((CompoundTag) element));
        }
        for (Tag element : tag.getList("routes", Tag.TAG_COMPOUND)) {
            state.putRoute(readRoute((CompoundTag) element));
        }
        for (Tag element : tag.getList("migrantGroups", Tag.TAG_COMPOUND)) {
            state.putMigrantGroup(readMigrantGroup((CompoundTag) element));
        }
        for (Tag element : tag.getList("livingRegions", Tag.TAG_COMPOUND)) {
            state.putLivingRegion(readRegion((CompoundTag) element));
        }
    }

    private static CompoundTag writeSettlement(SettlementState value) {
        CompoundTag settlement = new CompoundTag();
        settlement.putString("id", value.id().value());
        settlement.putInt("population", value.population());
        settlement.putInt("baseDefense", value.baseDefense());
        settlement.putInt("currentDefense", value.currentDefense());
        settlement.putInt("shortageSteps", value.shortageSteps());
        settlement.putString("status", value.status().name());
        ListTag stocks = new ListTag();
        value.stocks().forEach((resource, stock) -> {
            CompoundTag serialized = new CompoundTag();
            serialized.putString("resource", resource.name());
            serialized.putInt("capacity", stock.capacity());
            serialized.putInt("amount", stock.amount());
            stocks.add(serialized);
        });
        settlement.put("stocks", stocks);
        ListTag consumption = new ListTag();
        value.consumption().forEach((resource, amount) -> {
            CompoundTag serialized = new CompoundTag();
            serialized.putString("resource", resource.name());
            serialized.putInt("amount", amount);
            consumption.add(serialized);
        });
        settlement.put("consumption", consumption);
        return settlement;
    }

    private static SettlementState readSettlement(CompoundTag tag) {
        Map<ResourceKind, ResourceStock> stocks = new EnumMap<>(ResourceKind.class);
        for (Tag element : tag.getList("stocks", Tag.TAG_COMPOUND)) {
            CompoundTag stock = (CompoundTag) element;
            stocks.put(ResourceKind.valueOf(stock.getString("resource")),
                    new ResourceStock(stock.getInt("capacity"), stock.getInt("amount")));
        }
        Map<ResourceKind, Integer> consumption = new EnumMap<>(ResourceKind.class);
        for (Tag element : tag.getList("consumption", Tag.TAG_COMPOUND)) {
            CompoundTag need = (CompoundTag) element;
            consumption.put(ResourceKind.valueOf(need.getString("resource")), need.getInt("amount"));
        }
        return new SettlementState(new WorldObjectId(tag.getString("id")), tag.getInt("population"),
                tag.getInt("baseDefense"), stocks, consumption, tag.getInt("currentDefense"),
                tag.getInt("shortageSteps"), SettlementStatus.valueOf(tag.getString("status")));
    }

    private static CompoundTag writeRoute(RouteState value) {
        CompoundTag route = new CompoundTag();
        route.putString("id", value.id().value());
        route.putString("origin", value.origin().value());
        route.putString("destination", value.destination().value());
        route.putString("resource", value.resource().name());
        route.putInt("plannedCapacity", value.plannedCapacity());
        route.putInt("observedCapacity", value.observedCapacity());
        route.putString("status", value.status().name());
        return route;
    }

    private static RouteState readRoute(CompoundTag tag) {
        return new RouteState(new WorldObjectId(tag.getString("id")), new WorldObjectId(tag.getString("origin")),
                new WorldObjectId(tag.getString("destination")), ResourceKind.valueOf(tag.getString("resource")),
                tag.getInt("plannedCapacity"), tag.getInt("observedCapacity"), RouteStatus.valueOf(tag.getString("status")));
    }

    private static CompoundTag writeMigrantGroup(MigrantGroupState value) {
        CompoundTag migrants = new CompoundTag();
        migrants.putString("id", value.id().value());
        migrants.putString("origin", value.originSettlement().value());
        migrants.putInt("population", value.population());
        migrants.putString("status", value.status().name());
        return migrants;
    }

    private static MigrantGroupState readMigrantGroup(CompoundTag tag) {
        return new MigrantGroupState(new WorldObjectId(tag.getString("id")), new WorldObjectId(tag.getString("origin")),
                tag.getInt("population"), MigrantGroupStatus.valueOf(tag.getString("status")));
    }

    private static CompoundTag writeRegion(LivingRegionState value) {
        CompoundTag region = new CompoundTag();
        region.putString("id", value.id());
        region.putString("settlement", value.settlementId().value());
        region.putString("primaryFacility", value.primaryFacilityId().value());
        region.putString("alternateFacility", value.alternateFacilityId().value());
        region.putString("primaryRoute", value.primaryRouteId().value());
        region.putString("alternateRoute", value.alternateRouteId().value());
        region.putLong("crisisDelaySteps", value.crisisDelaySteps());
        if (value.primaryAudience() != null) region.putString("audience", value.primaryAudience().value());
        region.putString("status", value.status().name());
        region.putLong("discoveredAtStep", value.discoveredAtStep());
        return region;
    }

    private static LivingRegionState readRegion(CompoundTag tag) {
        StoryAudienceId audience = tag.contains("audience", Tag.TAG_STRING)
                ? new StoryAudienceId(tag.getString("audience")) : null;
        return new LivingRegionState(tag.getString("id"), new WorldObjectId(tag.getString("settlement")),
                new WorldObjectId(tag.getString("primaryFacility")), new WorldObjectId(tag.getString("alternateFacility")),
                new WorldObjectId(tag.getString("primaryRoute")), new WorldObjectId(tag.getString("alternateRoute")),
                tag.getLong("crisisDelaySteps"), audience, LivingRegionStatus.valueOf(tag.getString("status")),
                tag.getLong("discoveredAtStep"));
    }
}
