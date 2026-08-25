package io.farfrontier.palemirror.frontier.reference;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict all-or-nothing hydration of the settlement and individual-roster owner. */
final class ReferenceGrayboxStateSettlements {
    private ReferenceGrayboxStateSettlements() { }

    static LinkedHashMap<Integer, ReferenceSettlement> restore(
            Object encoded,
            ReferenceSimulationProfile profile,
            PythonRandom populationRng
    ) {
        PythonRandom requiredPopulationRng = java.util.Objects.requireNonNull(populationRng, "populationRng");
        LinkedHashMap<Integer, ReferenceSettlement> settlements = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "settlements")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "settlement map key");
            ReferenceSettlement settlement = settlement(entry.value(), profile, requiredPopulationRng);
            if (settlement.id() != id) throw new IllegalArgumentException("settlement map key does not match its id");
            if (settlements.putIfAbsent(id, settlement) != null) throw new IllegalArgumentException("duplicate settlement " + id);
        }
        return settlements;
    }

    private static ReferenceSettlement settlement(
            Object encoded,
            ReferenceSimulationProfile expectedProfile,
            PythonRandom populationRng
    ) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.settlement.Settlement", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "settlement fields",
                "id", "name", "x", "y", "population", "cash", "natural", "facilities", "stock", "primary_capacity",
                "doctrine", "profile", "population_rng", "residents", "alive", "integrity", "threat", "food_fulfillment",
                "medicine_fulfillment", "illness_burden", "last_investment_day", "last_strategy_day", "mobilized_personnel",
                "wounded_personnel", "daily_production", "daily_consumption");
        requireProfile(fields.get("profile"), expectedProfile);
        if (!java.util.Arrays.equals(populationRng.state().words(), ReferenceGrayboxStateReader.randomState(
                fields.get("population_rng"), "settlement population RNG").words())) {
            throw new IllegalArgumentException("settlement population RNG differs from the world stream");
        }
        int id = ReferenceGrayboxStateReader.integer(fields.get("id"), "settlement id");
        double population = ReferenceGrayboxStateReader.number(fields.get("population"), "settlement population");
        ReferenceSettlement result = new ReferenceSettlement(id,
                ReferenceGrayboxStateReader.string(fields.get("name"), "settlement name"),
                ReferenceGrayboxStateReader.integer(fields.get("x"), "settlement x"),
                ReferenceGrayboxStateReader.integer(fields.get("y"), "settlement y"),
                population,
                ReferenceGrayboxStateReader.number(fields.get("cash"), "settlement cash"),
                natural(fields.get("natural")), facilities(fields.get("facilities")), expectedProfile);
        result.populationRng(populationRng);
        result.doctrine(ReferenceGrayboxStateReader.string(fields.get("doctrine"), "settlement doctrine"));
        result.replaceStock(nonNegativeResources(fields.get("stock"), "settlement stock"));
        primaryCapacity(result, fields.get("primary_capacity"));
        result.alive(ReferenceGrayboxStateReader.bool(fields.get("alive"), "settlement alive"));
        result.integrity(ReferenceGrayboxStateReader.number(fields.get("integrity"), "settlement integrity"));
        result.threat(ReferenceGrayboxStateReader.number(fields.get("threat"), "settlement threat"));
        result.foodFulfillment(ReferenceGrayboxStateReader.number(fields.get("food_fulfillment"), "settlement food fulfillment"));
        result.medicineFulfillment(ReferenceGrayboxStateReader.number(fields.get("medicine_fulfillment"), "settlement medicine fulfillment"));
        result.illnessBurden(ReferenceGrayboxStateReader.number(fields.get("illness_burden"), "settlement illness burden"));
        result.lastInvestmentDay(ReferenceGrayboxStateReader.integer(fields.get("last_investment_day"), "settlement investment day"));
        result.lastStrategyDay(ReferenceGrayboxStateReader.integer(fields.get("last_strategy_day"), "settlement strategy day"));
        restoreDailyFlows(result, nonNegativeResources(fields.get("daily_production"), "settlement daily production"),
                nonNegativeResources(fields.get("daily_consumption"), "settlement daily consumption"));
        if (expectedProfile.discretePeople()) {
            result.restoreResidents(residents(fields.get("residents"), id));
            verifyProjection(result, population,
                    ReferenceGrayboxStateReader.number(fields.get("mobilized_personnel"), "settlement mobilized personnel"),
                    ReferenceGrayboxStateReader.number(fields.get("wounded_personnel"), "settlement wounded personnel"));
        } else if (fields.get("residents") != null) {
            throw new IllegalArgumentException("continuous settlement cannot restore individual residents");
        } else {
            result.population(population);
            result.mobilizedPersonnel(ReferenceGrayboxStateReader.number(fields.get("mobilized_personnel"), "settlement mobilized personnel"));
            result.woundedPersonnel(ReferenceGrayboxStateReader.number(fields.get("wounded_personnel"), "settlement wounded personnel"));
        }
        return result;
    }

    private static ReferenceNaturalPotential natural(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.settlement.NaturalPotential", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "natural potential", "fertility", "ore_richness", "forest", "river_power");
        return new ReferenceNaturalPotential(ReferenceGrayboxStateReader.number(fields.get("fertility"), "natural fertility"),
                ReferenceGrayboxStateReader.number(fields.get("ore_richness"), "natural ore richness"),
                ReferenceGrayboxStateReader.number(fields.get("forest"), "natural forest"),
                ReferenceGrayboxStateReader.number(fields.get("river_power"), "natural river power"));
    }

    private static ReferenceFacilities facilities(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.settlement.Facilities", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "facilities", "workshop", "armory", "clinic", "fortification");
        return new ReferenceFacilities(ReferenceGrayboxStateReader.number(fields.get("workshop"), "facility workshop"),
                ReferenceGrayboxStateReader.number(fields.get("armory"), "facility armory"),
                ReferenceGrayboxStateReader.number(fields.get("clinic"), "facility clinic"),
                ReferenceGrayboxStateReader.number(fields.get("fortification"), "facility fortification"));
    }

    private static void requireProfile(Object encoded, ReferenceSimulationProfile expected) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.profiles.SimulationProfile", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "settlement profile", "name", "person_scale", "discrete_people", "minimum_surviving_settlement");
        String id = ReferenceGrayboxStateReader.enumValue(fields.get("name"), "simulation.profiles.SimulationProfileName", "settlement profile name");
        if (!expected.id().equals(id)
                || expected.personScale() != ReferenceGrayboxStateReader.number(fields.get("person_scale"), "settlement person scale")
                || expected.discretePeople() != ReferenceGrayboxStateReader.bool(fields.get("discrete_people"), "settlement discrete people")
                || expected.minimumSurvivingSettlement() != ReferenceGrayboxStateReader.integer(fields.get("minimum_surviving_settlement"), "settlement survival minimum")) {
            throw new IllegalArgumentException("settlement profile differs from the world profile");
        }
    }

    private static ReferenceResidentLedger residents(Object encoded, int settlementId) {
        Map<String, Object> attributes = ReferenceGrayboxStateReader.typed(encoded, "simulation.population.ResidentLedger", "attributes");
        ReferenceGrayboxStateReader.exactKeys(attributes, "resident ledger", "next_ordinal", "residents", "revision", "settlement_id");
        if (ReferenceGrayboxStateReader.integer(attributes.get("settlement_id"), "resident settlement id") != settlementId) {
            throw new IllegalArgumentException("resident ledger belongs to another settlement");
        }
        List<ReferenceResidentLedger.ResidentState> people = new java.util.ArrayList<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(attributes.get("residents"), "resident ledger people")) {
            String id = ReferenceGrayboxStateReader.string(entry.key(), "resident map key");
            ReferenceResidentLedger.ResidentState resident = resident(entry.value());
            if (!id.equals(resident.id())) throw new IllegalArgumentException("resident map key does not match its id");
            people.add(resident);
        }
        return ReferenceResidentLedger.restore(new ReferenceResidentLedger.State(settlementId,
                ReferenceGrayboxStateReader.integer(attributes.get("next_ordinal"), "resident next ordinal"),
                ReferenceGrayboxStateReader.longValue(attributes.get("revision"), "resident revision"), people));
    }

    private static ReferenceResidentLedger.ResidentState resident(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.population.Resident", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "resident fields", "id", "home_settlement_id", "occupation", "economic_class",
                "employer_company_id", "location", "location_ref", "condition", "deployment_role");
        return new ReferenceResidentLedger.ResidentState(
                ReferenceGrayboxStateReader.string(fields.get("id"), "resident id"),
                ReferenceGrayboxStateReader.integer(fields.get("home_settlement_id"), "resident home settlement"),
                ReferenceGrayboxStateReader.string(fields.get("occupation"), "resident occupation"),
                ReferenceGrayboxStateReader.string(fields.get("economic_class"), "resident economic class"),
                ReferenceGrayboxStateReader.nullableInteger(fields.get("employer_company_id"), "resident employer"),
                ReferenceResidentLocation.valueOf(ReferenceGrayboxStateReader.enumValue(fields.get("location"),
                        "simulation.population.ResidentLocation", "resident location").toUpperCase(Locale.ROOT)),
                ReferenceGrayboxStateReader.nullableInteger(fields.get("location_ref"), "resident location reference"),
                ReferenceResidentCondition.valueOf(ReferenceGrayboxStateReader.enumValue(fields.get("condition"),
                        "simulation.population.ResidentCondition", "resident condition").toUpperCase(Locale.ROOT)),
                fields.get("deployment_role") == null ? null : ReferenceGrayboxStateReader.string(fields.get("deployment_role"), "resident deployment role"));
    }

    private static void primaryCapacity(ReferenceSettlement settlement, Object encoded) {
        Map<String, Double> values = stringNumbers(encoded, "primary capacity");
        if (!values.keySet().equals(java.util.Set.of("farm", "mine", "forest", "power"))) {
            throw new IllegalArgumentException("primary capacity has unexpected keys");
        }
        values.forEach(settlement::primaryCapacity);
    }

    private static void restoreDailyFlows(
            ReferenceSettlement settlement,
            Map<ReferenceResource, Double> production,
            Map<ReferenceResource, Double> consumption
    ) {
        settlement.resetDailyFlows();
        for (ReferenceResource resource : ReferenceResource.values()) {
            settlement.recordProduction(resource, production.get(resource));
            settlement.recordConsumption(resource, consumption.get(resource));
        }
    }

    private static EnumMap<ReferenceResource, Double> nonNegativeResources(Object encoded, String label) {
        EnumMap<ReferenceResource, Double> values = resources(encoded, label);
        if (values.values().stream().anyMatch(value -> value < 0.0d)) throw new IllegalArgumentException(label + " cannot be negative");
        return values;
    }

    private static EnumMap<ReferenceResource, Double> resources(Object encoded, String label) {
        EnumMap<ReferenceResource, Double> values = new EnumMap<>(ReferenceResource.class);
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            ReferenceResource resource = ReferenceResource.valueOf(ReferenceGrayboxStateReader.enumValue(entry.key(),
                    "simulation.economy.Resource", label + " resource").toUpperCase(Locale.ROOT));
            if (values.putIfAbsent(resource, ReferenceGrayboxStateReader.number(entry.value(), label + " amount")) != null) {
                throw new IllegalArgumentException(label + " has duplicate " + resource);
            }
        }
        if (values.size() != ReferenceResource.values().length) throw new IllegalArgumentException(label + " lacks a resource");
        return values;
    }

    private static Map<String, Double> stringNumbers(Object encoded, String label) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            String key = ReferenceGrayboxStateReader.string(entry.key(), label + " key");
            if (values.putIfAbsent(key, ReferenceGrayboxStateReader.number(entry.value(), label + " value")) != null) {
                throw new IllegalArgumentException(label + " has duplicate " + key);
            }
        }
        return values;
    }

    private static void verifyProjection(ReferenceSettlement settlement, double population, double mobilized, double wounded) {
        if (settlement.population() != population || settlement.mobilizedPersonnel() != mobilized || settlement.woundedPersonnel() != wounded) {
            throw new IllegalArgumentException("settlement individual roster does not match its aggregate projection");
        }
    }
}
