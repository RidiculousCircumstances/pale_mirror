package io.farfrontier.palemirror.frontier.v3.kernel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/** Pure stable topological plan for bounded staged execution. */
public final class DeterministicStagePlan<T> {
    public record Definition<T>(String id, int stage, Set<String> dependencies, Set<String> exclusiveWrites, T payload) {
        public Definition {
            id = requireId(id, "stage definition id");
            if (stage < 0) throw new IllegalArgumentException("stage must be non-negative: " + id);
            dependencies = normalized(dependencies, "stage dependency");
            exclusiveWrites = normalized(exclusiveWrites, "exclusive write kind");
            payload = Objects.requireNonNull(payload, "stage payload");
        }
    }

    private final List<Definition<T>> ordered;

    public DeterministicStagePlan(Collection<Definition<T>> definitions) {
        Map<String, Definition<T>> byId = new LinkedHashMap<>();
        Map<String, String> writeOwners = new HashMap<>();
        for (Definition<T> definition : List.copyOf(definitions)) {
            Objects.requireNonNull(definition, "stage definition");
            if (byId.putIfAbsent(definition.id(), definition) != null) throw new IllegalArgumentException("duplicate stage definition: " + definition.id());
            for (String write : definition.exclusiveWrites()) {
                String prior = writeOwners.putIfAbsent(write, definition.id());
                if (prior != null) throw new IllegalArgumentException("exclusive write " + write + " has owners " + prior + " and " + definition.id());
            }
        }
        for (Definition<T> definition : byId.values()) {
            for (String dependency : definition.dependencies()) {
                Definition<T> prerequisite = byId.get(dependency);
                if (prerequisite == null) throw new IllegalArgumentException("stage definition " + definition.id() + " requires missing dependency " + dependency);
                if (prerequisite.stage() > definition.stage()) {
                    throw new IllegalArgumentException("stage definition " + definition.id() + " depends on later stage " + dependency);
                }
            }
        }
        ordered = order(byId);
    }

    public List<Definition<T>> ordered() { return ordered; }

    private static <T> List<Definition<T>> order(Map<String, Definition<T>> byId) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Set<String>> dependents = new HashMap<>();
        for (Definition<T> definition : byId.values()) {
            indegree.put(definition.id(), definition.dependencies().size());
            for (String dependency : definition.dependencies()) {
                dependents.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(definition.id());
            }
        }
        Comparator<Definition<T>> stable = Comparator.comparingInt(Definition<T>::stage).thenComparing(Definition::id);
        PriorityQueue<Definition<T>> ready = new PriorityQueue<>(stable);
        byId.values().stream().filter(definition -> indegree.get(definition.id()) == 0).forEach(ready::add);
        List<Definition<T>> result = new ArrayList<>(byId.size());
        while (!ready.isEmpty()) {
            Definition<T> current = ready.remove(); result.add(current);
            for (String dependent : dependents.getOrDefault(current.id(), Set.of())) {
                int next = indegree.compute(dependent, (ignored, value) -> Objects.requireNonNull(value) - 1);
                if (next == 0) ready.add(byId.get(dependent));
            }
        }
        if (result.size() != byId.size()) {
            Set<String> cycle = new HashSet<>(byId.keySet()); result.forEach(definition -> cycle.remove(definition.id()));
            throw new IllegalArgumentException("staged execution dependency cycle: " + new java.util.TreeSet<>(cycle));
        }
        return List.copyOf(result);
    }

    private static Set<String> normalized(Set<String> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return Set.copyOf(values.stream().map(value -> requireId(value, role)).toList());
    }

    private static String requireId(String value, String role) {
        String normalized = Objects.requireNonNull(value, role).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(role + " must not be blank");
        return normalized;
    }
}
