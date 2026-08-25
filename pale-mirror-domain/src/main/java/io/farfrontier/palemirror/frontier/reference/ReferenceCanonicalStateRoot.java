package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * First owner mapper for {@code frontier_reference_state_v1}.
 *
 * <p>It deliberately has no domain mutation path. The Python codec labels
 * below are source contract labels, not Java implementation names.</p>
 */
final class ReferenceCanonicalStateRoot {
    private ReferenceCanonicalStateRoot() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        return object(
                "config", config(required.config()),
                "profile", profile(required.profile()),
                "day", required.day(),
                "rng", random(required.rng()),
                "population_rng", random(required.populationRng()),
                "events", sequence("list", required.events()));
    }

    private static Map<String, Object> config(ReferenceWorldConfig config) {
        return typed("simulation.world.WorldConfig", "fields", object(
                "width", config.width(),
                "height", config.height(),
                "settlement_count", config.settlementCount(),
                "seed", config.seed(),
                "infection_seeds", config.infectionSeeds(),
                "v2", config.v2(),
                "profile", config.profile().id()));
    }

    private static Map<String, Object> profile(ReferenceSimulationProfile profile) {
        return typed("simulation.profiles.SimulationProfile", "fields", object(
                "name", enumValue("simulation.profiles.SimulationProfileName", profile.id()),
                "person_scale", (double) profile.personScale(),
                "discrete_people", profile.discretePeople(),
                "minimum_surviving_settlement", profile.minimumSurvivingSettlement()));
    }

    private static Map<String, Object> random(PythonRandom value) {
        long[] words = value.state().words();
        List<Long> state = new ArrayList<>(words.length);
        for (long word : words) state.add(word);
        return object("$random_mt19937", object("version", 3, "state", List.copyOf(state), "gaussian_cache", null));
    }

    private static Map<String, Object> enumValue(String type, String value) {
        return object("$enum", type, "value", value);
    }

    private static Map<String, Object> typed(String type, String fieldName, Map<String, Object> fields) {
        return object("$type", type, fieldName, fields);
    }

    private static Map<String, Object> sequence(String type, List<?> items) {
        return object("$sequence", type, "items", List.copyOf(items));
    }

    private static Map<String, Object> object(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("object entries must be pairs");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return Collections.unmodifiableMap(result);
    }
}
