package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Source-shaped owner mapper for Python's {@code TradeNetwork}. */
final class ReferenceCanonicalStateTrade {
    private ReferenceCanonicalStateTrade() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        ReferenceTradeNetwork trade = required.trade();
        return typed("simulation.trade.TradeNetwork", "fields", object(
                "economy", economy(required.economy()),
                "routes", sequence("list", trade.routes().stream().map(ReferenceCanonicalStateTrade::route).toList()),
                "history", sequence("list", trade.history().stream().map(ReferenceCanonicalStateTrade::record).toList())));
    }

    private static Map<String, Object> economy(ReferenceEconomyEngine economy) {
        ReferenceSimulationProfile profile = economy.profile();
        return typed("simulation.economy.EconomyEngine", "attributes", object("profile",
                typed("simulation.profiles.SimulationProfile", "fields", object(
                        "name", enumValue("simulation.profiles.SimulationProfileName", profile.id()),
                        "person_scale", (double) profile.personScale(), "discrete_people", profile.discretePeople(),
                        "minimum_surviving_settlement", profile.minimumSurvivingSettlement()))));
    }

    private static Map<String, Object> route(ReferenceRoute value) {
        return typed("simulation.trade.Route", "fields", object(
                "a", value.a(), "b", value.b(), "distance", value.distance(), "capacity", value.capacity(), "risk", value.risk(),
                "quality", value.quality(), "infection", value.infection(), "quarantine_until", value.quarantineUntil(),
                "disruption_until", value.disruptionUntil(), "disruption_multiplier", value.disruptionMultiplier(),
                "checkpoint_capacity_multiplier", value.checkpointCapacityMultiplier(), "sector_keys", sequence("tuple", value.sectorKeys()),
                "sector_infection", value.sectorInfection()));
    }

    private static Map<String, Object> record(ReferenceTradeRecord value) {
        return typed("simulation.trade.TradeRecord", "fields", object(
                "day", value.day(), "seller_id", value.sellerId(), "buyer_id", value.buyerId(), "resource", resource(value.resource()),
                "shipped", value.shipped(), "delivered", value.delivered(), "unit_price", value.unitPrice(),
                "path", sequence("tuple", value.path())));
    }

    private static Map<String, Object> resource(ReferenceResource value) {
        return enumValue("simulation.economy.Resource", value.name().toLowerCase(Locale.ROOT));
    }

    private static Map<String, Object> enumValue(String type, String value) { return object("$enum", type, "value", value); }
    private static Map<String, Object> typed(String type, String fieldName, Map<String, Object> values) { return object("$type", type, fieldName, values); }
    private static Map<String, Object> sequence(String kind, List<?> items) {
        return object("$sequence", kind, "items", Collections.unmodifiableList(new ArrayList<>(items)));
    }

    private static Map<String, Object> object(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("object entries must be pairs");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return Collections.unmodifiableMap(result);
    }
}
