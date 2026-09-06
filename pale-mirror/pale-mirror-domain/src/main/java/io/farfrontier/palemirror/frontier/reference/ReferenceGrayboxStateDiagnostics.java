package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict reader for the bounded reporting records that remain part of state continuity. */
final class ReferenceGrayboxStateDiagnostics {
    private ReferenceGrayboxStateDiagnostics() { }

    static State read(Object encoded, Map<Integer, ReferenceSettlement> settlements) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.object(encoded, "diagnostics");
        ReferenceGrayboxStateReader.exactKeys(fields, "diagnostics", "history", "settlement_history", "combat_history", "containment_history");
        return new State(worldRows(fields.get("history")), settlementRows(fields.get("settlement_history"), settlements),
                combatRows(fields.get("combat_history"), settlements), containmentRows(fields.get("containment_history"), settlements));
    }

    private static List<ReferenceDailyWorldHistory> worldRows(Object encoded) {
        List<ReferenceDailyWorldHistory> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "world history")) {
            Map<String, Object> row = mapping(item, "world history row", "day", "alive", "population", "cash", "private_cash", "infection",
                    "swarms", "bioforms", "nests", "hive_biomass", "harvested_biomass", "ecology_organic", "ecology_scar", "feral_fraction",
                    "operations", "field_posts", "field_campaigns", "field_engagements", "objectives", "coalitions", "resource_sites", "contaminated_sites",
                    "trade_30d", "v2_chrysalises", "v2_emergencies");
            result.add(new ReferenceDailyWorldHistory(integerFloat(row.get("day"), "world history day"), integerFloat(row.get("alive"), "world history alive"),
                    number(row.get("population"), "world history population"), number(row.get("cash"), "world history cash"), number(row.get("private_cash"), "world history private cash"),
                    number(row.get("infection"), "world history infection"), integerFloat(row.get("swarms"), "world history swarms"), number(row.get("bioforms"), "world history bioforms"),
                    integerFloat(row.get("nests"), "world history nests"), number(row.get("hive_biomass"), "world history biomass"), number(row.get("harvested_biomass"), "world history harvested"),
                    number(row.get("ecology_organic"), "world history organic"), number(row.get("ecology_scar"), "world history scar"), number(row.get("feral_fraction"), "world history feral"),
                    integerFloat(row.get("operations"), "world history operations"), integerFloat(row.get("field_posts"), "world history posts"),
                    integerFloat(row.get("field_campaigns"), "world history campaigns"), integerFloat(row.get("field_engagements"), "world history engagements"),
                    integerFloat(row.get("objectives"), "world history objectives"), integerFloat(row.get("coalitions"), "world history coalitions"),
                    integerFloat(row.get("resource_sites"), "world history sites"), integerFloat(row.get("contaminated_sites"), "world history contaminated sites"),
                    numberOrInteger(row.get("trade_30d"), "world history trade"), integerFloat(row.get("v2_chrysalises"), "world history chrysalises"),
                    integerFloat(row.get("v2_emergencies"), "world history emergencies")));
        }
        return List.copyOf(result);
    }

    private static Map<Integer, List<ReferenceDailySettlementHistory>> settlementRows(Object encoded, Map<Integer, ReferenceSettlement> settlements) {
        LinkedHashMap<Integer, List<ReferenceDailySettlementHistory>> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "settlement history")) {
            int settlementId = ReferenceGrayboxStateReader.integer(entry.key(), "settlement history key");
            if (!settlements.containsKey(settlementId)) throw new IllegalArgumentException("settlement history owner is unknown");
            List<ReferenceDailySettlementHistory> rows = new ArrayList<>();
            for (Object item : ReferenceGrayboxStateReader.sequence(entry.value(), "list", "settlement history rows")) rows.add(settlementRow(item));
            if (result.putIfAbsent(settlementId, List.copyOf(rows)) != null) throw new IllegalArgumentException("duplicate settlement history owner");
        }
        return Map.copyOf(result);
    }

    private static ReferenceDailySettlementHistory settlementRow(Object encoded) {
        List<String> keys = new ArrayList<>(List.of("day", "alive", "population", "cash", "integrity", "threat", "illness_burden", "medicine_fulfillment", "wounded_personnel"));
        for (ReferenceResource resource : ReferenceResource.values()) {
            String prefix = resource.name().toLowerCase(Locale.ROOT);
            keys.add(prefix); keys.add(prefix + "_target"); keys.add(prefix + "_value"); keys.add(prefix + "_production"); keys.add(prefix + "_consumption");
        }
        Map<String, Object> row = mapping(encoded, "settlement history row", keys.toArray(String[]::new));
        EnumMap<ReferenceResource, ReferenceDailySettlementHistory.ReferenceResourceHistory> resources = new EnumMap<>(ReferenceResource.class);
        for (ReferenceResource resource : ReferenceResource.values()) {
            String prefix = resource.name().toLowerCase(Locale.ROOT);
            resources.put(resource, new ReferenceDailySettlementHistory.ReferenceResourceHistory(number(row.get(prefix), prefix),
                    number(row.get(prefix + "_target"), prefix + " target"), number(row.get(prefix + "_value"), prefix + " value"),
                    number(row.get(prefix + "_production"), prefix + " production"), number(row.get(prefix + "_consumption"), prefix + " consumption")));
        }
        double alive = number(row.get("alive"), "settlement history alive");
        if (alive != 0.0d && alive != 1.0d) throw new IllegalArgumentException("settlement history alive must be zero or one");
        return new ReferenceDailySettlementHistory(integerFloat(row.get("day"), "settlement history day"), alive == 1.0d, number(row.get("population"), "settlement history population"),
                number(row.get("cash"), "settlement history cash"), number(row.get("integrity"), "settlement history integrity"), number(row.get("threat"), "settlement history threat"),
                number(row.get("illness_burden"), "settlement history illness"), number(row.get("medicine_fulfillment"), "settlement history medicine"),
                number(row.get("wounded_personnel"), "settlement history wounded"), resources);
    }

    private static List<ReferenceCombatReceipt> combatRows(Object encoded, Map<Integer, ReferenceSettlement> settlements) {
        List<ReferenceCombatReceipt> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "combat history")) {
            Map<String, Object> row = mapping(item, "combat row", "day", "kind", "swarm_id", "settlement_id", "x", "y", "power", "composition", "phase", "defence", "support", "structural_breach", "damage", "destroyed");
            if (!"swarm_attack".equals(ReferenceGrayboxStateReader.string(row.get("kind"), "combat kind"))) throw new IllegalArgumentException("combat kind is unsupported");
            int settlementId = ReferenceGrayboxStateReader.integer(row.get("settlement_id"), "combat settlement");
            if (!settlements.containsKey(settlementId)) throw new IllegalArgumentException("combat settlement is unknown");
            result.add(new ReferenceCombatReceipt(ReferenceGrayboxStateReader.integer(row.get("day"), "combat day"), ReferenceGrayboxStateReader.integer(row.get("swarm_id"), "combat swarm"), settlementId,
                    ReferenceGrayboxStateReader.integer(row.get("x"), "combat x"), ReferenceGrayboxStateReader.integer(row.get("y"), "combat y"), number(row.get("power"), "combat power"),
                    ReferenceGrayboxStateReader.string(row.get("composition"), "combat composition"), formationPhase(ReferenceGrayboxStateReader.string(row.get("phase"), "combat phase")),
                    number(row.get("defence"), "combat defence"), number(row.get("support"), "combat support"), number(row.get("structural_breach"), "combat breach"),
                    number(row.get("damage"), "combat damage"), ReferenceGrayboxStateReader.bool(row.get("destroyed"), "combat destroyed")));
        }
        return List.copyOf(result);
    }

    private static List<ReferenceContainmentReceipt> containmentRows(Object encoded, Map<Integer, ReferenceSettlement> settlements) {
        List<ReferenceContainmentReceipt> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "containment history")) {
            Map<String, Object> row = mapping(item, "containment row", "day", "settlement_id", "x", "y", "radius", "strength", "ammo", "removed");
            int settlementId = ReferenceGrayboxStateReader.integer(row.get("settlement_id"), "containment settlement");
            if (!settlements.containsKey(settlementId)) throw new IllegalArgumentException("containment settlement is unknown");
            result.add(new ReferenceContainmentReceipt(ReferenceGrayboxStateReader.integer(row.get("day"), "containment day"), settlementId,
                    ReferenceGrayboxStateReader.integer(row.get("x"), "containment x"), ReferenceGrayboxStateReader.integer(row.get("y"), "containment y"),
                    number(row.get("radius"), "containment radius"), number(row.get("strength"), "containment strength"), number(row.get("ammo"), "containment ammo"), number(row.get("removed"), "containment removed")));
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> mapping(Object encoded, String label, String... keys) {
        Map<String, Object> result = ReferenceGrayboxStateReader.stringMap(encoded, label);
        ReferenceGrayboxStateReader.exactKeys(result, label, keys);
        return result;
    }

    private static double number(Object value, String label) { return ReferenceGrayboxStateReader.number(value, label); }
    private static ReferenceFormationPhase formationPhase(String id) {
        for (ReferenceFormationPhase value : ReferenceFormationPhase.values()) if (value.id().equals(id)) return value;
        throw new IllegalArgumentException("combat phase is unsupported");
    }
    private static double numberOrInteger(Object value, String label) { return value instanceof Integer integer ? integer.doubleValue() : number(value, label); }
    private static int integerFloat(Object value, String label) {
        double number = number(value, label);
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalArgumentException(label + " must be integral");
        return (int) number;
    }

    record State(List<ReferenceDailyWorldHistory> history, Map<Integer, List<ReferenceDailySettlementHistory>> settlementHistory,
                 List<ReferenceCombatReceipt> combatHistory, List<ReferenceContainmentReceipt> containmentHistory) {
        void applyTo(ReferenceWorldDiagnostics diagnostics) { diagnostics.restoreState(history, settlementHistory, combatHistory, containmentHistory); }
    }
}
