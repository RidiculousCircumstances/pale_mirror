package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Reporting-ledger owner mapper for {@code frontier_reference_state_v1}. */
final class ReferenceCanonicalStateDiagnostics {
    private ReferenceCanonicalStateDiagnostics() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        return object(
                "history", sequence(required.history().stream().map(ReferenceCanonicalStateDiagnostics::worldHistory).toList()),
                "settlement_history", settlementHistory(required.settlementHistory()),
                "combat_history", sequence(required.combatHistory().stream().map(ReferenceCanonicalStateDiagnostics::combat).toList()),
                "containment_history", sequence(required.containmentHistory().stream().map(ReferenceCanonicalStateDiagnostics::containment).toList()));
    }

    private static Map<String, Object> worldHistory(ReferenceDailyWorldHistory row) {
        return mapping(
                "day", (double) row.day(), "alive", (double) row.alive(), "population", row.population(), "cash", row.cash(),
                "private_cash", row.privateCash(), "infection", row.infection(), "swarms", (double) row.swarms(), "bioforms", row.bioforms(),
                "nests", (double) row.nests(), "hive_biomass", row.hiveBiomass(), "harvested_biomass", row.harvestedBiomass(),
                "ecology_organic", row.ecologyOrganic(), "ecology_scar", row.ecologyScar(), "feral_fraction", row.feralFraction(),
                "operations", (double) row.operations(), "field_posts", (double) row.fieldPosts(), "field_campaigns", (double) row.fieldCampaigns(),
                "field_engagements", (double) row.fieldEngagements(), "objectives", (double) row.objectives(), "coalitions", (double) row.coalitions(),
                "resource_sites", (double) row.resourceSites(), "contaminated_sites", (double) row.contaminatedSites(), "trade_30d", pythonTradeSum(row.trade30d()),
                "v2_chrysalises", (double) row.v2Chrysalises(), "v2_emergencies", (double) row.v2Emergencies());
    }

    private static Map<String, Object> settlementHistory(Map<Integer, List<ReferenceDailySettlementHistory>> rows) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, List<ReferenceDailySettlementHistory>> entry : rows.entrySet()) {
            pairs.add(pair(entry.getKey(), sequence(entry.getValue().stream().map(ReferenceCanonicalStateDiagnostics::settlement).toList())));
        }
        return map(pairs);
    }

    private static Map<String, Object> settlement(ReferenceDailySettlementHistory row) {
        List<Object> entries = new ArrayList<>(List.of(
                "day", (double) row.day(), "alive", row.alive() ? 1.0d : 0.0d, "population", row.population(), "cash", row.cash(),
                "integrity", row.integrity(), "threat", row.threat(), "illness_burden", row.illnessBurden(),
                "medicine_fulfillment", row.medicineFulfillment(), "wounded_personnel", row.woundedPersonnel()));
        for (ReferenceResource resource : ReferenceResource.values()) {
            String prefix = resource.name().toLowerCase(Locale.ROOT);
            ReferenceDailySettlementHistory.ReferenceResourceHistory history = row.resources().get(resource);
            if (history == null) throw new IllegalStateException("settlement history lacks resource " + resource);
            Collections.addAll(entries, prefix, history.amount(), prefix + "_target", history.target(), prefix + "_value", history.value(),
                    prefix + "_production", history.production(), prefix + "_consumption", history.consumption());
        }
        return mapping(entries.toArray());
    }

    private static Map<String, Object> combat(ReferenceCombatReceipt row) {
        return mapping(
                "day", row.day(), "kind", "swarm_attack", "swarm_id", row.swarmId(), "settlement_id", row.settlementId(),
                "x", row.x(), "y", row.y(), "power", row.power(), "composition", row.composition(), "phase", row.phase().id(),
                "defence", row.defence(), "support", row.support(), "structural_breach", row.structuralBreach(), "damage", row.damage(),
                "destroyed", row.destroyed());
    }

    private static Map<String, Object> containment(ReferenceContainmentReceipt row) {
        return mapping("day", row.day(), "settlement_id", row.settlementId(), "x", row.x(), "y", row.y(), "radius", row.radius(),
                "strength", row.strength(), "ammo", row.ammo(), "removed", row.removed());
    }

    /** Python's {@code sum()} produces its integer start value when the trade list is empty. */
    private static Number pythonTradeSum(double value) {
        if (value == 0.0d) return Integer.valueOf(0);
        return Double.valueOf(value);
    }

    private static Map<String, Object> sequence(List<?> items) { return object("$sequence", "list", "items", List.copyOf(items)); }

    private static Map<String, Object> mapping(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("mapping entries must be pairs");
        List<List<Object>> pairs = new ArrayList<>();
        for (int index = 0; index < entries.length; index += 2) pairs.add(pair(entries[index], entries[index + 1]));
        return map(pairs);
    }

    private static Map<String, Object> map(List<List<Object>> pairs) {
        pairs.sort(Comparator.comparing(pair -> ReferenceV2PublicSnapshot.canonicalJson(pair.getFirst())));
        return object("$map", List.copyOf(pairs));
    }

    private static List<Object> pair(Object key, Object value) {
        ArrayList<Object> result = new ArrayList<>(2);
        result.add(key);
        result.add(value);
        return Collections.unmodifiableList(result);
    }

    private static Map<String, Object> object(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("object entries must be pairs");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return Collections.unmodifiableMap(result);
    }
}
