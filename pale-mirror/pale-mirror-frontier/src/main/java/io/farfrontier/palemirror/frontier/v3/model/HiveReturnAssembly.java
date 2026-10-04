package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exact surviving-member return cursors for one completed hive expedition.
 *
 * <p>The parent keeps its original roster in {@link HiveMobilization}; this value names only
 * the living members that still own a body to bring home.  A casualty is therefore neither
 * recreated nor silently dropped from the expedition receipt.</p>
 */
public record HiveReturnAssembly(SubjectId nestId, Map<SubjectId, HiveTaskAssembly.Member> members) {
    public HiveReturnAssembly {
        nestId = Objects.requireNonNull(nestId, "hive return nest");
        Map<SubjectId, HiveTaskAssembly.Member> copy = new LinkedHashMap<>();
        Objects.requireNonNull(members, "hive return members").forEach((actor, member) -> {
            if (copy.put(Objects.requireNonNull(actor, "hive return actor"),
                    Objects.requireNonNull(member, "hive return member")) != null) {
                throw new IllegalArgumentException("duplicate hive return actor");
            }
        });
        if (copy.size() > HiveMobilization.MAX_MEMBERS
                || copy.values().stream().map(HiveTaskAssembly.Member::destinationSurface).distinct().count() != copy.size()
                || copy.values().stream().map(HiveTaskAssembly.Member::currentSurface).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("hive return members or home surfaces are invalid");
        }
        members = Map.copyOf(copy);
    }

    public boolean complete() { return members.values().stream().allMatch(HiveTaskAssembly.Member::arrived); }

    /** Remove one acknowledged casualty from locomotion, not from the parent's original roster. */
    public HiveReturnAssembly withoutCasualty(SubjectId actorId) {
        Objects.requireNonNull(actorId, "exact returning casualty");
        if (!members.containsKey(actorId)) throw new IllegalArgumentException("casualty is not a returning participant");
        var next = new LinkedHashMap<>(members);
        next.remove(actorId);
        return new HiveReturnAssembly(nestId, next);
    }

    /** Exact collision-free COLD candidates; HOT checks the same retained edge. */
    public List<SubjectId> safeAdvances() {
        List<SubjectId> result = new ArrayList<>();
        members.keySet().stream().sorted().forEach(actor -> {
            HiveTaskAssembly.Member member = members.get(actor);
            if (!member.arrived() && members.entrySet().stream().noneMatch(other -> !other.getKey().equals(actor)
                    && other.getValue().currentSurface().equals(member.nextSurface()))) result.add(actor);
        });
        return List.copyOf(result);
    }

    public HiveReturnAssembly advance(SubjectId actorId) {
        SubjectId actor = Objects.requireNonNull(actorId, "hive return advancing actor");
        if (!safeAdvances().contains(actor)) throw new IllegalArgumentException("hive return next cursor is occupied");
        Map<SubjectId, HiveTaskAssembly.Member> next = new LinkedHashMap<>(members);
        HiveTaskAssembly.Member current = next.get(actor);
        next.put(actor, new HiveTaskAssembly.Member(current.topology(), current.cursor() + 1));
        return new HiveReturnAssembly(nestId, next);
    }
}
