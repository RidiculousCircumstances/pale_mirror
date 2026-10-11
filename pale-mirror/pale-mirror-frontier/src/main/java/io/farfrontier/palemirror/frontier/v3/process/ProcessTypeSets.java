package io.farfrontier.palemirror.frontier.v3.process;

import java.util.*;

/** Immutable composition of explicitly named wire contracts; no discovery or dispatch. */
final class ProcessTypeSets {
    private ProcessTypeSets() { }
    static Set<String> types(String... values) { return Set.copyOf(List.of(values)); }
    @SafeVarargs static Set<String> union(Set<String>... values) {
        var result = new LinkedHashSet<String>();
        for (var value : values) result.addAll(value);
        return Set.copyOf(result);
    }
}
