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
        previous.forEach((id, value) -> {
            if (!java.util.Objects.equals(value, next.get(id))) {
                points.apply(value).forEach(result::add);
                V replacement = next.get(id);
                if (replacement != null) points.apply(replacement).forEach(result::add);
            }
        });
        next.forEach((id, value) -> {
            if (!previous.containsKey(id)) points.apply(value).forEach(result::add);
        });
        return Set.copyOf(result);
    }
}
