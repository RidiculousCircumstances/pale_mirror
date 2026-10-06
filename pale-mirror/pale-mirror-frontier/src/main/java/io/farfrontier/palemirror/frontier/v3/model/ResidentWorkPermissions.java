package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Settlement-owned authorization only. Executions, cell claims and body ownership live elsewhere. */
public record ResidentWorkPermissions(Map<ResidentWorkKind, Map<SubjectId, Integer>> priorities,
                                      Map<ResidentWorkKind, Integer> minimumLocalStaff) {
    public static final int MAX_WORKERS_PER_KIND = 64;

    public ResidentWorkPermissions {
        EnumMap<ResidentWorkKind, Map<SubjectId, Integer>> copy = new EnumMap<>(ResidentWorkKind.class);
        Objects.requireNonNull(priorities, "work permissions").forEach((kind, residents) -> {
            Map<SubjectId, Integer> exact = Map.copyOf(residents);
            if (exact.size() > MAX_WORKERS_PER_KIND)
                throw new IllegalArgumentException("work permission roster exceeds bounded capacity");
            if (exact.values().stream().anyMatch(priority -> priority < 1 || priority > 255))
                throw new IllegalArgumentException("enabled work priority must be between one and 255");
            copy.put(Objects.requireNonNull(kind, "work permission kind"), exact);
        });
        priorities = Map.copyOf(copy);
        minimumLocalStaff = Map.copyOf(minimumLocalStaff);
        for (var reserve : minimumLocalStaff.entrySet())
            if (!copy.containsKey(reserve.getKey()) || reserve.getValue() < 0
                    || reserve.getValue() > copy.get(reserve.getKey()).size())
                throw new IllegalArgumentException("local staffing reserve exceeds its authorized roster");
        minimumLocalStaff = minimumLocalStaff.entrySet().stream().filter(entry -> entry.getValue() != 0)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    /** Explicit all-equal policy, useful for authorities with no local staffing reservation. */
    public ResidentWorkPermissions(Map<ResidentWorkKind, Set<SubjectId>> workers) {
        this(workers.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().stream().collect(
                        java.util.stream.Collectors.toUnmodifiableMap(id -> id, id -> 1)))), Map.of());
    }
    public static ResidentWorkPermissions none() { return new ResidentWorkPermissions(Map.of()); }
    public Map<ResidentWorkKind, Set<SubjectId>> workers() {
        return priorities.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().keySet()));
    }
    public Set<SubjectId> workers(ResidentWorkKind kind) { return priorities.getOrDefault(kind, Map.of()).keySet(); }
    public int priority(ResidentWorkKind kind, SubjectId residentId) {
        return priorities.getOrDefault(kind, Map.of()).getOrDefault(residentId, Integer.MAX_VALUE);
    }
    public int localReserve(ResidentWorkKind kind) { return minimumLocalStaff.getOrDefault(kind, 0); }
    public boolean permits(ResidentWorkKind kind, SubjectId residentId) { return workers(kind).contains(residentId); }
    public ResidentWorkPermissions withoutResident(SubjectId residentId) {
        var next = new EnumMap<ResidentWorkKind, Map<SubjectId, Integer>>(ResidentWorkKind.class);
        var reserve = new EnumMap<ResidentWorkKind, Integer>(ResidentWorkKind.class);
        priorities.forEach((kind, residents) -> {
            next.put(kind, residents.entrySet().stream().filter(entry -> !entry.getKey().equals(residentId))
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
            reserve.put(kind, Math.min(localReserve(kind), next.get(kind).size()));
        });
        return new ResidentWorkPermissions(next, reserve);
    }
}
