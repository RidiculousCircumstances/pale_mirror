package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Strict source-shaped primitives shared by the V2 state readers. */
final class ReferenceGrayboxStateV2Support {
    private ReferenceGrayboxStateV2Support() { }

    static ReferenceSimulationProfile profile(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.profiles.SimulationProfile", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 profile", "name", "person_scale", "discrete_people", "minimum_surviving_settlement");
        String id = ReferenceGrayboxStateReader.enumValue(fields.get("name"), "simulation.profiles.SimulationProfileName", "V2 profile name");
        ReferenceSimulationProfile profile = ReferenceSimulationProfile.GRAYBOX_1_40;
        if (!profile.id().equals(id) || profile.personScale() != number(fields.get("person_scale"), "V2 person scale")
                || profile.discretePeople() != ReferenceGrayboxStateReader.bool(fields.get("discrete_people"), "V2 discrete people")
                || profile.minimumSurvivingSettlement() != ReferenceGrayboxStateReader.integer(fields.get("minimum_surviving_settlement"), "V2 survival minimum")) {
            throw new IllegalArgumentException("V2 profile differs from graybox_1_40");
        }
        return profile;
    }

    static LinkedHashMap<String, Object> stringMap(Object encoded, String label) {
        return new LinkedHashMap<>(ReferenceGrayboxStateReader.stringMap(encoded, label));
    }

    static LinkedHashMap<Integer, Double> doubles(Object encoded, String label) {
        LinkedHashMap<Integer, Double> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int key = ReferenceGrayboxStateReader.integer(entry.key(), label + " key");
            if (result.putIfAbsent(key, number(entry.value(), label + " value")) != null) throw new IllegalArgumentException(label + " has duplicate key");
        }
        return result;
    }

    static LinkedHashMap<Integer, Integer> integers(Object encoded, String label) {
        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int key = ReferenceGrayboxStateReader.integer(entry.key(), label + " key");
            if (result.putIfAbsent(key, ReferenceGrayboxStateReader.integer(entry.value(), label + " value")) != null) {
                throw new IllegalArgumentException(label + " has duplicate key");
            }
        }
        return result;
    }

    static List<String> strings(Object encoded, String label) {
        List<String> result = new ArrayList<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, "tuple", label)) result.add(ReferenceGrayboxStateReader.string(value, label + " value"));
        if (new LinkedHashSet<>(result).size() != result.size()) throw new IllegalArgumentException(label + " has duplicate value");
        return List.copyOf(result);
    }

    static List<Integer> integerTuple(Object encoded, String label) {
        List<Integer> result = new ArrayList<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, "tuple", label)) {
            result.add(ReferenceGrayboxStateReader.integer(value, label + " value"));
        }
        if (new LinkedHashSet<>(result).size() != result.size()) throw new IllegalArgumentException(label + " has duplicate value");
        return List.copyOf(result);
    }

    static List<ReferenceGridPosition> positions(Object encoded, String label) {
        List<ReferenceGridPosition> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "tuple", label)) {
            List<Object> pair = ReferenceGrayboxStateReader.sequence(item, "tuple", label + " position");
            if (pair.size() != 2) throw new IllegalArgumentException(label + " position is invalid");
            result.add(new ReferenceGridPosition(ReferenceGrayboxStateReader.integer(pair.get(0), label + " x"),
                    ReferenceGrayboxStateReader.integer(pair.get(1), label + " y")));
        }
        if (new LinkedHashSet<>(result).size() != result.size() || result.isEmpty()) throw new IllegalArgumentException(label + " is invalid");
        return List.copyOf(result);
    }

    static ReferenceRouteKey routeKey(Object encoded, String label) {
        List<Object> values = ReferenceGrayboxStateReader.sequence(encoded, "tuple", label);
        if (values.size() != 2) throw new IllegalArgumentException(label + " is invalid");
        int first = ReferenceGrayboxStateReader.integer(values.get(0), label + " first settlement");
        int second = ReferenceGrayboxStateReader.integer(values.get(1), label + " second settlement");
        if (first >= second) throw new IllegalArgumentException(label + " is not normalized");
        return new ReferenceRouteKey(first, second);
    }

    static List<ReferenceRouteKey> routeKeys(Object encoded, String label) {
        List<ReferenceRouteKey> result = new ArrayList<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, "tuple", label)) result.add(routeKey(value, label + " route"));
        if (new LinkedHashSet<>(result).size() != result.size()) throw new IllegalArgumentException(label + " has duplicate route");
        return List.copyOf(result);
    }

    static LinkedHashMap<Integer, List<String>> residents(Object encoded, String label) {
        LinkedHashMap<Integer, List<String>> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int settlement = ReferenceGrayboxStateReader.integer(entry.key(), label + " settlement");
            if (result.putIfAbsent(settlement, strings(entry.value(), label + " residents")) != null) {
                throw new IllegalArgumentException(label + " has duplicate settlement");
            }
        }
        return result;
    }

    static LinkedHashMap<Integer, Map<ReferenceHumanUnitKind, Double>> composition(Object encoded, String label) {
        LinkedHashMap<Integer, Map<ReferenceHumanUnitKind, Double>> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int settlement = ReferenceGrayboxStateReader.integer(entry.key(), label + " settlement");
            LinkedHashMap<ReferenceHumanUnitKind, Double> roles = new LinkedHashMap<>();
            for (ReferenceGrayboxStateReader.Entry role : ReferenceGrayboxStateReader.mapEntries(entry.value(), label + " roles")) {
                ReferenceHumanUnitKind kind = enumById(role.key(), "simulation.formations.HumanUnitKind", ReferenceHumanUnitKind.values(),
                        ReferenceHumanUnitKind::id, label + " role");
                if (roles.putIfAbsent(kind, number(role.value(), label + " role amount")) != null) throw new IllegalArgumentException(label + " has duplicate role");
            }
            if (result.putIfAbsent(settlement, Map.copyOf(roles)) != null) throw new IllegalArgumentException(label + " has duplicate settlement");
        }
        return result;
    }

    static ReferenceResource resource(Object encoded, String label) {
        String value = ReferenceGrayboxStateReader.enumValue(encoded, "simulation.economy.Resource", label);
        try { return ReferenceResource.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException(label + " is unsupported", error); }
    }

    static <E extends Enum<E>> E enumById(
            Object encoded,
            String type,
            E[] values,
            Function<E, String> id,
            String label
    ) {
        String value = ReferenceGrayboxStateReader.enumValue(encoded, type, label);
        for (E item : values) if (id.apply(item).equals(value)) return item;
        throw new IllegalArgumentException(label + " is unsupported");
    }

    static double number(Object value, String label) { return ReferenceGrayboxStateReader.number(value, label); }

    static void sequence(int next, Iterable<Integer> ids, String label) {
        if (next < 1) throw new IllegalArgumentException(label + " next identity is invalid");
        for (int id : ids) if (id < 1 || id >= next) throw new IllegalArgumentException(label + " identity sequence is invalid");
    }

    static <K, V> Map<K, V> immutable(Map<K, V> values) {
        return Map.copyOf(new LinkedHashMap<>(values));
    }

    static <T> List<T> immutable(List<T> values) { return List.copyOf(values); }

    static <T> Set<T> set(List<T> values, String label) {
        LinkedHashSet<T> result = new LinkedHashSet<>(values);
        if (result.size() != values.size()) throw new IllegalArgumentException(label + " has duplicate value");
        return Set.copyOf(result);
    }
}
