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
        var turn = new Turn(nanos, clock);
        // A disabled owner or recovery-only turn may never reach admission.
        // That absence cannot permanently park the cursor after all live owners.
        if (!candidates.isEmpty()) next = (next + 1) % candidates.size();
        return turn;
    }
    final class Turn {
        private final long started, budget;
        private final LongSupplier clock;
        private final int first = next;
        private boolean admitted;
        Turn(long budget, LongSupplier clock) { this.budget = budget; this.clock = clock; started = clock.getAsLong(); }
        boolean admit(String id) {
            int index = candidates.indexOf(id);
            if (index < 0) throw new IllegalArgumentException("undeclared preparation candidate " + id);
            if (index < first || admitted && clock.getAsLong() - started >= budget) return false;
            admitted = true; next = (index + 1) % candidates.size(); return true;
        }
    }
}
