package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Settlement-owned authorization only. Executions, cell claims and body ownership live elsewhere. */
public record ResidentWorkPermissions(Map<ResidentWorkKind, Set<SubjectId>> workers) {
    public static final int MAX_WORKERS_PER_KIND = 64;

    public ResidentWorkPermissions {
        EnumMap<ResidentWorkKind, Set<SubjectId>> copy = new EnumMap<>(ResidentWorkKind.class);
        Objects.requireNonNull(workers, "work permissions").forEach((kind, residents) -> {
            Set<SubjectId> exact = Set.copyOf(residents);
            if (exact.size() > MAX_WORKERS_PER_KIND)
                throw new IllegalArgumentException("work permission roster exceeds bounded capacity");
            copy.put(Objects.requireNonNull(kind, "work permission kind"), exact);
        });
        workers = Map.copyOf(copy);
    }
    public static ResidentWorkPermissions none() { return new ResidentWorkPermissions(Map.of()); }
    public Set<SubjectId> workers(ResidentWorkKind kind) { return workers.getOrDefault(kind, Set.of()); }
    public boolean permits(ResidentWorkKind kind, SubjectId residentId) { return workers(kind).contains(residentId); }
    public ResidentWorkPermissions withoutResident(SubjectId residentId) {
        var next = new EnumMap<ResidentWorkKind, Set<SubjectId>>(ResidentWorkKind.class);
        workers.forEach((kind, residents) -> next.put(kind, residents.stream().filter(id -> !id.equals(residentId))
                .collect(java.util.stream.Collectors.toUnmodifiableSet())));
        return new ResidentWorkPermissions(next);
    }
}
