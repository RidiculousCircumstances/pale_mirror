package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One rebuildable physical candidate pass; neither a semantic goal nor a route owner. */
record FrontierV3GoalAlternatives(List<SurfaceAnchor> candidates, Set<SurfaceAnchor> rejected) {
    FrontierV3GoalAlternatives {
        candidates = List.copyOf(candidates); rejected = Set.copyOf(rejected);
        if (candidates.isEmpty() || candidates.stream().distinct().count() != candidates.size()
                || !candidates.containsAll(rejected)) throw new IllegalArgumentException("invalid local goal alternatives");
    }
    FrontierV3GoalAlternatives(List<SurfaceAnchor> candidates) { this(candidates, Set.of()); }

    boolean matches(List<SurfaceAnchor> available) {
        return candidates.size() == available.size() && Set.copyOf(candidates).equals(Set.copyOf(available));
    }
    FrontierV3GoalAlternatives nextPassIfExhausted() {
        return rejected.size() == candidates.size() ? new FrontierV3GoalAlternatives(candidates) : this;
    }
    List<SurfaceAnchor> batch() {
        return candidates.stream().filter(candidate -> !rejected.contains(candidate))
                .limit(MovementOrder.MAX_LEGAL_STATIONS).toList();
    }
    FrontierV3GoalAlternatives reject(Collection<SurfaceAnchor> failed) {
        var next = new HashSet<>(rejected); next.addAll(failed);
        return new FrontierV3GoalAlternatives(candidates, next);
    }

    /** A pedestrian obstruction needs courtesy; a yielded search budget needs continuation. */
    static boolean reconsider(FrontierV3GoalNavigation.Result result) {
        return result.status() == FrontierV3GoalNavigation.Status.BLOCKED && result.blockReason().filter(reason -> switch (reason) {
            case PATH_UNAVAILABLE, PATH_STALLED, TARGET_CHUNK_UNLOADED, OFF_CONTRACT, UNSUPPORTED_MEDIUM -> true;
            case TRAFFIC_BLOCKED, SEARCH_BUDGET_EXHAUSTED, UNSUPPORTED_CAPABILITY -> false;
        }).isPresent();
    }
}
