package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Logistics owns waiting age; lots retain only material lineage and title. */
public record HaulAccumulation(Map<Key, Long> firstAvailable) {
    public static final int MAX_ROWS = 16384;
    public record Key(SubjectId home, SubjectId account, String itemKind) implements Comparable<Key> {
        public Key { Objects.requireNonNull(home); Objects.requireNonNull(account); Objects.requireNonNull(itemKind); }
        public int compareTo(Key other) {
            int order = home.compareTo(other.home); if (order == 0) order = account.compareTo(other.account);
            return order == 0 ? itemKind.compareTo(other.itemKind) : order;
        }
    }
    public HaulAccumulation {
        firstAvailable = ImmutableLookupMap.copyOf(Objects.requireNonNull(firstAvailable));
        if (firstAvailable.size() > MAX_ROWS || firstAvailable.values().stream().anyMatch(tick -> tick < 0))
            throw new IllegalArgumentException("invalid bounded haul accumulation");
    }
    public static HaulAccumulation empty() { return new HaulAccumulation(Map.of()); }
    public HaulAccumulation reviewed(SubjectId home, Set<Key> available, long tick) {
        if (tick < 0 || available.stream().anyMatch(key -> !key.home().equals(home)))
            throw new IllegalArgumentException("foreign haul accumulation review");
        var next = new LinkedHashMap<>(firstAvailable);
        next.keySet().removeIf(key -> key.home().equals(home) && !available.contains(key));
        for (var key : available) next.putIfAbsent(key, tick);
        return next.equals(firstAvailable) ? this : new HaulAccumulation(next);
    }
}
