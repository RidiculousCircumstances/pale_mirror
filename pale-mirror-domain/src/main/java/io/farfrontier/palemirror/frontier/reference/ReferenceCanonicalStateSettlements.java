package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Source-shaped owner mapper for Python's {@code dict[int, Settlement]}. */
final class ReferenceCanonicalStateSettlements {
    private ReferenceCanonicalStateSettlements() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceSettlement> entry : required.marketWorld().settlements().entrySet()) {
            if (entry.getValue().residents() != null) {
                throw new IllegalStateException("source settlement codec does not yet encode individual resident ledger " + entry.getKey());
            }
            pairs.add(pair(entry.getKey(), settlement(entry.getValue(), required.populationRng())));
        }
        return map(pairs);
    }

    private static Map<String, Object> settlement(ReferenceSettlement value, PythonRandom populationRng) {
        return typed("simulation.settlement.Settlement", "fields", object(
                "id", value.id(), "name", value.name(), "x", value.x(), "y", value.y(), "population", value.population(), "cash", value.cash(),
                "natural", natural(value.natural()), "facilities", facilities(value.facilities()), "stock", resources(value.stock()),
                "primary_capacity", primaryCapacity(value), "doctrine", value.doctrine(), "profile", profile(value.profile()),
                "population_rng", random(populationRng), "residents", null, "alive", value.alive(), "integrity", value.integrity(),
                "threat", value.threat(), "food_fulfillment", value.foodFulfillment(), "medicine_fulfillment", value.medicineFulfillment(),
                "illness_burden", value.illnessBurden(), "last_investment_day", value.lastInvestmentDay(),
                "last_strategy_day", value.lastStrategyDay(), "mobilized_personnel", value.mobilizedPersonnel(),
                "wounded_personnel", value.woundedPersonnel(), "daily_production", resources(value.dailyProduction()),
                "daily_consumption", resources(value.dailyConsumption())));
    }

    private static Map<String, Object> natural(ReferenceNaturalPotential value) {
        return typed("simulation.settlement.NaturalPotential", "fields", object("fertility", value.fertility(),
                "ore_richness", value.oreRichness(), "forest", value.forest(), "river_power", value.riverPower()));
    }

    private static Map<String, Object> facilities(ReferenceFacilities value) {
        return typed("simulation.settlement.Facilities", "fields", object("workshop", value.workshop(), "armory", value.armory(),
                "clinic", value.clinic(), "fortification", value.fortification()));
    }

    private static Map<String, Object> primaryCapacity(ReferenceSettlement settlement) {
        return mapping("farm", settlement.primaryCapacity("farm"), "mine", settlement.primaryCapacity("mine"),
                "forest", settlement.primaryCapacity("forest"), "power", settlement.primaryCapacity("power"));
    }

    private static Map<String, Object> resources(Map<ReferenceResource, Double> values) {
        List<List<Object>> pairs = new ArrayList<>();
        for (ReferenceResource resource : ReferenceResource.values()) {
            Double value = values.get(resource);
            if (value == null) throw new IllegalStateException("settlement ledger lacks resource " + resource);
            pairs.add(pair(resource(resource), value));
        }
        return map(pairs);
    }

    private static Map<String, Object> profile(ReferenceSimulationProfile profile) {
        return typed("simulation.profiles.SimulationProfile", "fields", object(
                "name", enumValue("simulation.profiles.SimulationProfileName", profile.id()),
                "person_scale", (double) profile.personScale(), "discrete_people", profile.discretePeople(),
                "minimum_surviving_settlement", profile.minimumSurvivingSettlement()));
    }

    private static Map<String, Object> random(PythonRandom value) {
        List<Long> state = new ArrayList<>();
        for (long word : value.state().words()) state.add(word);
        return object("$random_mt19937", object("version", 3, "state", List.copyOf(state), "gaussian_cache", null));
    }

    private static Map<String, Object> resource(ReferenceResource value) {
        return enumValue("simulation.economy.Resource", value.name().toLowerCase(Locale.ROOT));
    }

    private static Map<String, Object> enumValue(String type, String value) { return object("$enum", type, "value", value); }
    private static Map<String, Object> typed(String type, String fieldName, Map<String, Object> values) { return object("$type", type, fieldName, values); }

    private static Map<String, Object> mapping(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("mapping entries must be pairs");
        List<List<Object>> pairs = new ArrayList<>();
        for (int index = 0; index < entries.length; index += 2) pairs.add(pair(entries[index], entries[index + 1]));
        return map(pairs);
    }

    private static Map<String, Object> map(List<List<Object>> pairs) {
        List<List<Object>> ordered = new ArrayList<>(pairs);
        ordered.sort(Comparator.comparing(pair -> ReferenceV2PublicSnapshot.canonicalJson(pair.getFirst())));
        return object("$map", List.copyOf(ordered));
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
