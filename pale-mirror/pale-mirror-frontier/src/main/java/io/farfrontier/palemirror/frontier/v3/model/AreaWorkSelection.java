package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;

/**
 * Selects a semantic work target from a revisioned area's retained target pool.
 *
 * <p>The owning process supplies target state and a deterministic priority; this
 * helper neither owns navigation nor treats a list position as completed work.
 * A target may be deferred while another pending target is selected. Ties use
 * stable plan order only to make replay independent of map iteration order.</p>
 */
public final class AreaWorkSelection {
    private AreaWorkSelection() { }

    public static OptionalInt choose(int targetCount, IntPredicate pending,
                                     IntToLongFunction priority) {
        if (targetCount < 0)
            throw new IllegalArgumentException("area work target count is outside its bound");
        Objects.requireNonNull(pending, "area work pending targets");
        Objects.requireNonNull(priority, "area work target priority");
        int choice = -1;
        long best = Long.MAX_VALUE;
        for (int index = 0; index < targetCount; index++) {
            if (!pending.test(index)) continue;
            long score = priority.applyAsLong(index);
            if (score < 0) throw new IllegalArgumentException("area work priority cannot be negative");
            if (choice == -1 || score < best) {
                choice = index;
                best = score;
            }
        }
        return choice < 0 ? OptionalInt.empty() : OptionalInt.of(choice);
    }

    /** Bounded continuation without rescoring the whole area after every receipt. */
    public static OptionalInt nextAfter(int targetCount, int previousIndex, IntPredicate preferred,
                                        IntPredicate pending) {
        if (targetCount < 0 || previousIndex < -1 || previousIndex >= targetCount)
            throw new IllegalArgumentException("area continuation has a foreign target index");
        Objects.requireNonNull(preferred, "preferred area targets");
        Objects.requireNonNull(pending, "pending area targets");
        for (int pass = 0; pass < 2; pass++) {
            for (int offset = 1; offset <= targetCount; offset++) {
                int index = (previousIndex + offset) % targetCount;
                if (pending.test(index) && (pass == 1 || preferred.test(index)))
                    return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /** Search another pending target for which the caller has an actual reachability witness. */
    public static OptionalInt reachableAfter(int targetCount, int previousIndex, IntPredicate reachable) {
        if (targetCount < 0 || previousIndex < 0 || previousIndex >= targetCount)
            throw new IllegalArgumentException("area reachability search has a foreign target index");
        Objects.requireNonNull(reachable, "area reachability witness");
        for (int offset = 1; offset < targetCount; offset++) {
            int index = (previousIndex + offset) % targetCount;
            if (reachable.test(index)) return OptionalInt.of(index);
        }
        return OptionalInt.empty();
    }
}
