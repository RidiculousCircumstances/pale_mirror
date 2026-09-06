package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict source-shaped value reader shared by graybox hydration owners. */
final class ReferenceGrayboxStateReader {
    static final String REFERENCE_CODEC = "frontier_reference_state_v1";

    private ReferenceGrayboxStateReader() { }

    static Map<String, Object> envelope(Map<String, Object> state) {
        Map<String, Object> required = object(state, "graybox state envelope");
        exactKeys(required, "graybox state envelope", "codec", "reference_state", "bioform_identities");
        if (!ReferenceGrayboxCanonicalState.CODEC.equals(string(required.get("codec"), "graybox state codec"))) {
            throw new IllegalArgumentException("graybox state document codec is invalid");
        }
        object(required.get("reference_state"), "graybox reference state");
        mapEntries(required.get("bioform_identities"), "graybox bioform identities");
        return required;
    }

    static Map<String, Object> referenceState(Object value) {
        Map<String, Object> state = object(value, "graybox reference state");
        exactKeys(state, "graybox reference state", "codec", "config", "profile", "day", "rng", "population_rng", "events",
                "diagnostics", "settlements", "resource_sites", "trade", "market", "infection", "operations", "field", "v2");
        if (!REFERENCE_CODEC.equals(string(state.get("codec"), "reference state codec"))) {
            throw new IllegalArgumentException("graybox reference state codec is invalid");
        }
        return state;
    }

    static Map<String, Object> typed(Object value, String type, String member) {
        Map<String, Object> typed = object(value, type);
        exactKeys(typed, type, "$type", member);
        if (!type.equals(string(typed.get("$type"), type + " type"))) {
            throw new IllegalArgumentException(type + " has an unexpected type marker");
        }
        return object(typed.get(member), type + " " + member);
    }

    static Map<String, Object> object(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(label + " must be a map");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException(label + " has a non-string key");
            if (result.containsKey(key)) throw new IllegalArgumentException(label + " has a duplicate key " + key);
            result.put(key, entry.getValue());
        }
        return result;
    }

    static Object required(Map<String, Object> values, String key, String label) {
        if (!values.containsKey(key)) throw new IllegalArgumentException(label + " is missing " + key);
        return values.get(key);
    }

    static String string(Object value, String label) {
        if (!(value instanceof String result)) throw new IllegalArgumentException(label + " must be a string");
        return result;
    }

    static boolean bool(Object value, String label) {
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException(label + " must be a boolean");
        return result;
    }

    static int integer(Object value, String label) {
        if (!(value instanceof Integer result)) throw new IllegalArgumentException(label + " must be an integer");
        return result;
    }

    static Integer nullableInteger(Object value, String label) {
        return value == null ? null : integer(value, label);
    }

    static long longValue(Object value, String label) {
        if (!(value instanceof Long result)) throw new IllegalArgumentException(label + " must be a long");
        return result;
    }

    static double number(Object value, String label) {
        if (!(value instanceof Double result) || !Double.isFinite(result)) {
            throw new IllegalArgumentException(label + " must be a finite float");
        }
        return result;
    }

    static List<Object> sequence(Object value, String kind, String label) {
        Map<String, Object> sequence = object(value, label);
        exactKeys(sequence, label, "$sequence", "items");
        if (!kind.equals(string(sequence.get("$sequence"), label + " kind"))) {
            throw new IllegalArgumentException(label + " has unexpected sequence kind");
        }
        return list(sequence.get("items"), label + " items");
    }

    static List<Object> list(Object value, String label) {
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException(label + " must be a list");
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    static List<Entry> mapEntries(Object value, String label) {
        Map<String, Object> map = object(value, label);
        exactKeys(map, label, "$map");
        if (!(map.get("$map") instanceof List<?> pairs)) throw new IllegalArgumentException(label + " pairs must be a list");
        List<Entry> result = new ArrayList<>(pairs.size());
        for (Object pair : pairs) {
            if (!(pair instanceof List<?> values) || values.size() != 2) throw new IllegalArgumentException(label + " pair is invalid");
            result.add(new Entry(values.get(0), values.get(1)));
        }
        return List.copyOf(result);
    }

    static Map<String, Object> stringMap(Object value, String label) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Entry entry : mapEntries(value, label)) {
            String key = string(entry.key(), label + " key");
            if (result.putIfAbsent(key, entry.value()) != null) throw new IllegalArgumentException(label + " has duplicate " + key);
        }
        return result;
    }

    static PythonRandom.State randomState(Object encoded, String label) {
        Map<String, Object> random = object(encoded, label);
        exactKeys(random, label, "$random_mt19937");
        Map<String, Object> fields = object(random.get("$random_mt19937"), label + " fields");
        exactKeys(fields, label + " fields", "version", "state", "gaussian_cache");
        if (integer(fields.get("version"), label + " version") != 3 || fields.get("gaussian_cache") != null) {
            throw new IllegalArgumentException(label + " is unsupported");
        }
        List<Object> words = list(fields.get("state"), label + " state");
        long[] values = new long[words.size()];
        for (int index = 0; index < values.length; index++) values[index] = longValue(words.get(index), label + " word");
        return new PythonRandom.State(values);
    }

    static String enumValue(Object value, String type, String label) {
        Map<String, Object> encoded = object(value, label);
        exactKeys(encoded, label, "$enum", "value");
        if (!type.equals(string(encoded.get("$enum"), label + " enum type"))) {
            throw new IllegalArgumentException(label + " has unexpected enum type");
        }
        return string(encoded.get("value"), label + " enum value");
    }

    static void exactKeys(Map<String, Object> values, String label, String... expected) {
        Set<String> wanted = new LinkedHashSet<>(List.of(expected));
        if (!values.keySet().equals(wanted)) {
            throw new IllegalArgumentException(label + " has unexpected keys " + values.keySet());
        }
    }

    record Entry(Object key, Object value) {
        Entry {
            Objects.requireNonNull(key, "key");
        }
    }
}
