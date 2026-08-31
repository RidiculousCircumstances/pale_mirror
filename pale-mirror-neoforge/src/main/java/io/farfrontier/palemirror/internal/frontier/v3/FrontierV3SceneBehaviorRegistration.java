package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Plain-Java fail-closed registration validation, kept loadable by fast unit tests. */
final class FrontierV3SceneBehaviorRegistration {
    private FrontierV3SceneBehaviorRegistration() { }

    static void requireCompleteKinds(List<SceneCauseKind> kinds) {
        Objects.requireNonNull(kinds, "scene behavior kinds");
        Set<SceneCauseKind> distinct = new HashSet<>();
        for (SceneCauseKind kind : kinds) {
            if (!distinct.add(Objects.requireNonNull(kind, "scene kind"))) throw new IllegalArgumentException("duplicate NeoForge scene behavior: " + kind);
        }
        Set<SceneCauseKind> missing = new HashSet<>(Set.of(SceneCauseKind.values()));
        missing.removeAll(distinct);
        if (!missing.isEmpty()) throw new IllegalArgumentException("missing NeoForge scene behavior: " + missing);
    }
}
