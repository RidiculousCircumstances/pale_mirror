package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Preserves vanilla's selected batch size while letting ready chunks pass deferred neighbours. */
public final class FrontierV3PresentationBatch {
    private FrontierV3PresentationBatch() { }
    public static <T> List<T> select(List<T> selected, Supplier<Stream<T>> pending,
                                    Predicate<T> ready, Consumer<T> requeue, Consumer<T> take) {
        var result = new ArrayList<T>(selected.size());
        var held = new java.util.HashSet<T>();
        for (T chunk : selected) {
            if (ready.test(chunk)) result.add(chunk);
            else { held.add(chunk); requeue.accept(chunk); }
        }
        int remaining = selected.size() - result.size();
        if (remaining > 0) {
            try (var candidates = pending.get()) {
                var replacements = candidates.filter(chunk -> !held.contains(chunk) && ready.test(chunk))
                        .limit(remaining).toList();
                for (T chunk : replacements) { take.accept(chunk); result.add(chunk); }
            }
        }
        return List.copyOf(result);
    }
}
