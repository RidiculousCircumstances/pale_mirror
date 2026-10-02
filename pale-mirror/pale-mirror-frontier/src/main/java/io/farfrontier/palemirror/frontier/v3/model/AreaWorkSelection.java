package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.List;
import java.util.function.IntFunction;
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

    public enum SpatialPolicy { SPREAD_STARTS, LOCAL_CONTINUATION }

    /** Soft spatial ownership: prefer targets nearer this worker than any peer's work target.
     * No target is excluded by proximity; once the local region is exhausted, share the rest.
     * This is target ranking only, not a path, reservation or persisted territory.
     */
    public static OptionalInt spatiallySeparated(int targetCount, IntPredicate pending,
            SurfaceAnchor origin, IntFunction<SurfaceAnchor> targets, List<SurfaceAnchor> peers, SpatialPolicy policy) {
        Objects.requireNonNull(origin); Objects.requireNonNull(targets); Objects.requireNonNull(peers);
        Objects.requireNonNull(pending); Objects.requireNonNull(policy);
        if (targetCount < 0) throw new IllegalArgumentException("area work target count is outside its bound");
        IntToLongFunction distance = index -> distance(origin, targets.apply(index));
        if (policy == SpatialPolicy.SPREAD_STARTS && !peers.isEmpty()) {
            int choice = -1; long separation = -1; long travel = Long.MAX_VALUE;
            for (int index = 0; index < targetCount; index++) {
                if (!pending.test(index)) continue;
                SurfaceAnchor target = targets.apply(index);
                long spacing = peers.stream().mapToLong(peer -> distance(peer, target)).min().orElseThrow();
                long approach = distance.applyAsLong(index);
                if (spacing > separation || spacing == separation && approach < travel) {
                    choice = index; separation = spacing; travel = approach;
                }
            }
            return choice < 0 ? OptionalInt.empty() : OptionalInt.of(choice);
        }
        OptionalInt local = choose(targetCount, index -> pending.test(index)
                && peers.stream().allMatch(peer -> distance(peer, targets.apply(index)) > distance.applyAsLong(index)), distance);
        return local.isPresent() ? local : choose(targetCount, pending, distance);
    }

    private static long distance(SurfaceAnchor from, SurfaceAnchor to) {
        return Math.abs((long) from.x() - to.x()) + Math.abs((long) from.y() - to.y())
                + Math.abs((long) from.z() - to.z());
    }

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
