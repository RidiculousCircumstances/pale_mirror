package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lossless graybox-state contract layered over the source-shaped V2 graph.
 *
 * <p>The Python V2 state encoder deliberately exposes declared {@code Swarm}
 * fields only, so its private discrete-body identity ledger needs an explicit
 * extension. Residents already belong to the ordinary source graph through
 * their settlement ledger. This snapshot is an interchange contract for the
 * eventual persistence codec; it never authorizes reconstruction of missing
 * people or bodies.</p>
 */
final class ReferenceGrayboxCanonicalState {
    static final String CODEC = "frontier_graybox_state_v1";

    private ReferenceGrayboxCanonicalState() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        ReferenceGrayboxLayout.requireSupported(required);
        LinkedHashMap<String, Object> state = new LinkedHashMap<>();
        state.put("codec", CODEC);
        state.put("reference_state", ReferenceCanonicalState.capture(required));
        state.put("bioform_identities", bioformIdentities(required));
        return Collections.unmodifiableMap(state);
    }

    private static Map<String, Object> bioformIdentities(ReferenceWorld world) {
        List<List<Object>> swarmPairs = new ArrayList<>();
        for (ReferenceSwarm swarm : world.infection().swarms()) {
            List<List<Object>> kindPairs = new ArrayList<>();
            for (Map.Entry<ReferenceBioformKind, List<String>> entry : swarm.bioformIds().entrySet()) {
                kindPairs.add(pair(bioformKind(entry.getKey()), sequence(entry.getValue())));
            }
            swarmPairs.add(pair(swarm.id(), map(kindPairs)));
        }
        return map(swarmPairs);
    }

    private static Map<String, Object> bioformKind(ReferenceBioformKind value) {
        return object("$enum", "simulation.infection.BioformKind", "value", value.id());
    }

    private static Map<String, Object> sequence(List<String> values) {
        return object("$sequence", "list", "items", List.copyOf(values));
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
