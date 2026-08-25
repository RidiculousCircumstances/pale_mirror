package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict all-or-nothing hydration reader for routes and bounded trade history. */
final class ReferenceGrayboxStateTrade {
    private ReferenceGrayboxStateTrade() { }

    static State read(Object encoded, ReferenceSimulationProfile profile, Map<Integer, ReferenceSettlement> settlements) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.trade.TradeNetwork", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "trade fields", "economy", "routes", "history");
        requireEconomy(fields.get("economy"), profile);
        return new State(routes(fields.get("routes"), settlements), history(fields.get("history"), settlements));
    }

    private static void requireEconomy(Object encoded, ReferenceSimulationProfile profile) {
        Map<String, Object> attributes = ReferenceGrayboxStateReader.typed(encoded, "simulation.economy.EconomyEngine", "attributes");
        ReferenceGrayboxStateReader.exactKeys(attributes, "trade economy", "profile");
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(attributes.get("profile"), "simulation.profiles.SimulationProfile", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "trade economy profile", "name", "person_scale", "discrete_people", "minimum_surviving_settlement");
        String id = ReferenceGrayboxStateReader.enumValue(fields.get("name"), "simulation.profiles.SimulationProfileName", "trade profile name");
        if (!profile.id().equals(id) || profile.personScale() != ReferenceGrayboxStateReader.number(fields.get("person_scale"), "trade person scale")
                || profile.discretePeople() != ReferenceGrayboxStateReader.bool(fields.get("discrete_people"), "trade discrete people")
                || profile.minimumSurvivingSettlement() != ReferenceGrayboxStateReader.integer(fields.get("minimum_surviving_settlement"), "trade survival minimum")) {
            throw new IllegalArgumentException("trade economy profile differs from the world profile");
        }
    }

    private static List<ReferenceRoute> routes(Object encoded, Map<Integer, ReferenceSettlement> settlements) {
        List<ReferenceRoute> result = new ArrayList<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, "list", "trade routes")) result.add(route(value, settlements));
        return List.copyOf(result);
    }

    private static ReferenceRoute route(Object encoded, Map<Integer, ReferenceSettlement> settlements) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.trade.Route", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "trade route", "a", "b", "distance", "capacity", "risk", "quality", "infection",
                "quarantine_until", "disruption_until", "disruption_multiplier", "checkpoint_capacity_multiplier", "sector_keys", "sector_infection");
        int a = ReferenceGrayboxStateReader.integer(fields.get("a"), "route A");
        int b = ReferenceGrayboxStateReader.integer(fields.get("b"), "route B");
        if (!settlements.containsKey(a) || !settlements.containsKey(b)) throw new IllegalArgumentException("route endpoint is unknown");
        ReferenceRoute result = new ReferenceRoute(a, b, ReferenceGrayboxStateReader.number(fields.get("distance"), "route distance"),
                ReferenceGrayboxStateReader.number(fields.get("capacity"), "route capacity"));
        result.risk(ReferenceGrayboxStateReader.number(fields.get("risk"), "route risk"));
        result.quality(ReferenceGrayboxStateReader.number(fields.get("quality"), "route quality"));
        result.infection(ReferenceGrayboxStateReader.number(fields.get("infection"), "route infection"));
        result.quarantineUntil(ReferenceGrayboxStateReader.integer(fields.get("quarantine_until"), "route quarantine"));
        result.disruptionUntil(ReferenceGrayboxStateReader.integer(fields.get("disruption_until"), "route disruption"));
        result.disruptionMultiplier(ReferenceGrayboxStateReader.number(fields.get("disruption_multiplier"), "route disruption multiplier"));
        result.checkpointCapacityMultiplier(ReferenceGrayboxStateReader.number(fields.get("checkpoint_capacity_multiplier"), "route checkpoint multiplier"));
        result.sectorKeys(strings(fields.get("sector_keys"), "tuple", "route sectors"));
        result.sectorInfection(ReferenceGrayboxStateReader.number(fields.get("sector_infection"), "route sector infection"));
        return result;
    }

    private static List<ReferenceTradeRecord> history(Object encoded, Map<Integer, ReferenceSettlement> settlements) {
        List<ReferenceTradeRecord> result = new ArrayList<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, "list", "trade history")) {
            Map<String, Object> fields = ReferenceGrayboxStateReader.typed(value, "simulation.trade.TradeRecord", "fields");
            ReferenceGrayboxStateReader.exactKeys(fields, "trade record", "day", "seller_id", "buyer_id", "resource", "shipped", "delivered", "unit_price", "path");
            int seller = ReferenceGrayboxStateReader.integer(fields.get("seller_id"), "trade seller");
            int buyer = ReferenceGrayboxStateReader.integer(fields.get("buyer_id"), "trade buyer");
            if (!settlements.containsKey(seller) || !settlements.containsKey(buyer)) throw new IllegalArgumentException("trade record owner is unknown");
            result.add(new ReferenceTradeRecord(ReferenceGrayboxStateReader.integer(fields.get("day"), "trade day"), seller, buyer,
                    ReferenceResource.valueOf(ReferenceGrayboxStateReader.enumValue(fields.get("resource"), "simulation.economy.Resource", "trade resource")
                            .toUpperCase(Locale.ROOT)), ReferenceGrayboxStateReader.number(fields.get("shipped"), "trade shipped"),
                    ReferenceGrayboxStateReader.number(fields.get("delivered"), "trade delivered"),
                    ReferenceGrayboxStateReader.number(fields.get("unit_price"), "trade unit price"),
                    integers(fields.get("path"), "tuple", "trade path")));
        }
        return List.copyOf(result);
    }

    private static List<String> strings(Object encoded, String kind, String label) {
        List<String> result = new ArrayList<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, kind, label)) result.add(ReferenceGrayboxStateReader.string(value, label + " key"));
        return List.copyOf(result);
    }

    private static List<Integer> integers(Object encoded, String kind, String label) {
        List<Integer> result = new ArrayList<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, kind, label)) result.add(ReferenceGrayboxStateReader.integer(value, label + " item"));
        return List.copyOf(result);
    }

    record State(List<ReferenceRoute> routes, List<ReferenceTradeRecord> history) {
        State { routes = List.copyOf(routes); history = List.copyOf(history); }
        void applyTo(ReferenceTradeNetwork trade) { trade.restoreState(routes, history); }
    }
}
