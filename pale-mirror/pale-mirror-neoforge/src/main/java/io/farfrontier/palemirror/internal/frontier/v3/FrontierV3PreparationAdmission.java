package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.List;
import java.util.function.LongSupplier;

/** Shared soft fence for explicitly deferrable preparation, never effects or
 * recovery. Stable order survives; a rotating first candidate cannot starve. */
final class FrontierV3PreparationAdmission {
    private final List<String> candidates;
    private int next;
    FrontierV3PreparationAdmission(List<String> candidates) { this.candidates = List.copyOf(candidates); }
    Turn begin(long nanos, LongSupplier clock) {
        // A native tick may resume only observations or a suffix of the physical
        // plan. Only an encountered candidate can advance preparation fairness.
        return new Turn(nanos, clock);
    }
    final class Turn {
        private final long started, budget;
        private final LongSupplier clock;
        private final int first = next;
        private boolean admitted;
        private String requested;
        Turn(long budget, LongSupplier clock) { this.budget = budget; this.clock = clock; started = clock.getAsLong(); }
        boolean admit(String id) {
            int index = candidates.indexOf(id);
            if (index < 0) throw new IllegalArgumentException("undeclared preparation candidate " + id);
            requested = id;
            if (index < first || admitted && clock.getAsLong() - started >= budget) return false;
            admitted = true; next = (index + 1) % candidates.size(); return true;
        }
        void completeCandidate(String id) {
            int index = candidates.indexOf(id);
            if (index < 0) throw new IllegalArgumentException("undeclared preparation candidate " + id);
            // An encountered owner may have only recovery work or be disabled.
            // A denied actual request must retain its turn; an absent request must not.
            if (!id.equals(requested) && next == index) next = (index + 1) % candidates.size();
        }
    }
}
