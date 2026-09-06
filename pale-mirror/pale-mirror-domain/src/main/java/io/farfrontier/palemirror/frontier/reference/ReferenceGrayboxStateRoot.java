package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Strict immutable root owner read before any graybox world hydration starts. */
final class ReferenceGrayboxStateRoot {
    private ReferenceGrayboxStateRoot() { }

    static State read(Object encoded) {
        Map<String, Object> state = ReferenceGrayboxStateReader.referenceState(encoded);
        ReferenceWorldConfig config = config(state.get("config"));
        requireProfile(state.get("profile"), config.profile());
        int day = ReferenceGrayboxStateReader.integer(state.get("day"), "reference day");
        if (day < 0) throw new IllegalArgumentException("reference day cannot be negative");
        return new State(config, day, ReferenceGrayboxStateReader.randomState(state.get("rng"), "reference RNG"),
                ReferenceGrayboxStateReader.randomState(state.get("population_rng"), "reference population RNG"),
                events(state.get("events")));
    }

    private static ReferenceWorldConfig config(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.world.WorldConfig", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "reference world config", "width", "height", "settlement_count", "seed",
                "infection_seeds", "v2", "profile");
        ReferenceSimulationProfile profile = profile(ReferenceGrayboxStateReader.string(fields.get("profile"), "reference config profile"));
        ReferenceWorldConfig config = new ReferenceWorldConfig(
                ReferenceGrayboxStateReader.integer(fields.get("width"), "reference width"),
                ReferenceGrayboxStateReader.integer(fields.get("height"), "reference height"),
                ReferenceGrayboxStateReader.integer(fields.get("settlement_count"), "reference settlement count"),
                ReferenceGrayboxStateReader.longValue(fields.get("seed"), "reference seed"),
                ReferenceGrayboxStateReader.integer(fields.get("infection_seeds"), "reference infection seeds"),
                ReferenceGrayboxStateReader.bool(fields.get("v2"), "reference V2 flag"), profile);
        if (!config.v2() || config.width() != ReferenceWorldConfig.SOURCE_WIDTH || config.height() != ReferenceWorldConfig.SOURCE_HEIGHT
                || config.settlementCount() != ReferenceWorldConfig.SOURCE_SETTLEMENT_COUNT
                || config.infectionSeeds() != ReferenceWorldConfig.SOURCE_INFECTION_SEEDS) {
            throw new IllegalArgumentException("graybox root has an unsupported world configuration");
        }
        return config;
    }

    private static void requireProfile(Object encoded, ReferenceSimulationProfile expected) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.profiles.SimulationProfile", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "reference profile", "name", "person_scale", "discrete_people", "minimum_surviving_settlement");
        String id = ReferenceGrayboxStateReader.enumValue(fields.get("name"), "simulation.profiles.SimulationProfileName", "reference profile name");
        if (!expected.id().equals(id) || expected.personScale() != ReferenceGrayboxStateReader.number(fields.get("person_scale"), "reference person scale")
                || expected.discretePeople() != ReferenceGrayboxStateReader.bool(fields.get("discrete_people"), "reference discrete people")
                || expected.minimumSurvivingSettlement() != ReferenceGrayboxStateReader.integer(fields.get("minimum_surviving_settlement"), "reference survival minimum")) {
            throw new IllegalArgumentException("reference profile differs from the world configuration");
        }
    }

    private static ReferenceSimulationProfile profile(String id) {
        if (!ReferenceSimulationProfile.GRAYBOX_1_40.id().equals(id)) {
            throw new IllegalArgumentException("graybox root has an unsupported profile");
        }
        return ReferenceSimulationProfile.GRAYBOX_1_40;
    }

    private static List<String> events(Object encoded) {
        List<Object> values = ReferenceGrayboxStateReader.sequence(encoded, "list", "reference events");
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) result.add(ReferenceGrayboxStateReader.string(value, "reference event"));
        return List.copyOf(result);
    }

    record State(ReferenceWorldConfig config, int day, PythonRandom.State rng, PythonRandom.State populationRng, List<String> events) {
        State {
            if (!ReferenceSimulationProfile.GRAYBOX_1_40.equals(config.profile())) {
                throw new IllegalArgumentException("graybox root state requires graybox_1_40");
            }
            events = List.copyOf(events);
        }
    }
}
