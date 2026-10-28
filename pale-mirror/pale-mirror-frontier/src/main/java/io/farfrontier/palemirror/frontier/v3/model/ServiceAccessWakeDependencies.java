package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/** Changed owner records address both removed and newly declared service points. */
final class ServiceAccessWakeDependencies {
    private ServiceAccessWakeDependencies() { }

    static <K, V> Set<SubjectId> changed(Map<K, V> previous, Map<K, V> next,
                                        Function<V, Stream<SubjectId>> points) {
        if (previous == next) return Set.of();
        var result = new HashSet<SubjectId>();
        ImmutableMapChanges.forEach(previous, next, change -> {
            if (change.before() != null) points.apply(change.before()).forEach(result::add);
            if (change.after() != null) points.apply(change.after()).forEach(result::add);
        });
        return Set.copyOf(result);
    }
}
