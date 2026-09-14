package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Volatile round-robin observation only; immutable canonical state still owns actor identity/order. */
final class FrontierV3ActorProbeSchedule {
    private static final Map<FrontierV3ServerRuntime<?, ?>, Cursor> CURSORS = new IdentityHashMap<>();

    private FrontierV3ActorProbeSchedule() { }

    static List<SubjectId> next(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state, int maximum, int advance) {
        return CURSORS.computeIfAbsent(runtime, ignored -> new Cursor(state.actorLocations().keySet().stream().sorted().toList()))
                .next(maximum, advance);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); }

    static final class Cursor {
        private final List<SubjectId> actors;
        private int next;

        Cursor(List<SubjectId> actors) { this.actors = List.copyOf(actors); }

        List<SubjectId> next(int maximum, int advance) {
            if (maximum < 1 || advance < 1 || advance > maximum) throw new IllegalArgumentException("invalid probe window");
            if (actors.isEmpty()) return List.of();
            List<SubjectId> slice = new java.util.ArrayList<>(Math.min(maximum, actors.size()));
            int start = next;
            for (int count = 0; count < maximum; count++) {
                slice.add(actors.get((start + count) % actors.size()));
            }
            next = (start + advance) % actors.size();
            return List.copyOf(slice);
        }
    }
}
