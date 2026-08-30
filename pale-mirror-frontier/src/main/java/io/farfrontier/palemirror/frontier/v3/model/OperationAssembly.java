package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Exact durable approaches of a named convoy before it may start route travel. */
public record OperationAssembly(Map<SubjectId, Member> members, SubjectId cargoCarrierId, Optional<OperationAssemblyDeferral> deferral) {
    public static final int MAX_COLD_ADVANCE = 32;
    public OperationAssembly(Map<SubjectId, Member> members, SubjectId cargoCarrierId) { this(members, cargoCarrierId, Optional.empty()); }
    public OperationAssembly {
        Objects.requireNonNull(members, "assembly members"); cargoCarrierId = Objects.requireNonNull(cargoCarrierId, "assembly cargo carrier"); deferral = Objects.requireNonNull(deferral, "assembly deferral");
        Map<SubjectId, Member> copy = new LinkedHashMap<>();
        members.forEach((actor, member) -> {
            if (copy.put(Objects.requireNonNull(actor, "assembly actor"), Objects.requireNonNull(member, "assembly member")) != null) throw new IllegalArgumentException("duplicate assembly actor");
        });
        if (copy.isEmpty() || copy.size() > 8 || !copy.containsKey(cargoCarrierId)) throw new IllegalArgumentException("assembly requires its cargo carrier and 1..8 members");
        if (copy.values().stream().map(Member::destination).distinct().count() != copy.size()) throw new IllegalArgumentException("assembly destinations must be distinct");
        if (deferral.isPresent()) {
            OperationAssemblyDeferral blocked = deferral.orElseThrow(); Member member = copy.get(blocked.actorId());
            if (member == null || member.arrived() || !member.corridor().get(member.cursor() + 1).equals(blocked.target())) {
                throw new IllegalArgumentException("assembly deferral must name one exact next member cursor");
            }
        }
        members = Map.copyOf(copy);
    }
    public boolean complete() { return members.values().stream().allMatch(Member::arrived); }
    public Map<SubjectId, BlockPosition> positions() { Map<SubjectId, BlockPosition> result = new LinkedHashMap<>(); members.forEach((actor, member) -> result.put(actor, member.currentPosition())); return Map.copyOf(result); }

    /** Advances only the already compiled approaches; callers cannot retarget a convoy mid-assembly. */
    public OperationAssembly advance(Map<SubjectId, Member> nextMembers) {
        Objects.requireNonNull(nextMembers, "next assembly members");
        if (!members.keySet().equals(nextMembers.keySet())) throw new IllegalArgumentException("assembly advance changes formation");
        Map<SubjectId, Member> next = new LinkedHashMap<>();
        SubjectId advancedActor = null;
        for (Map.Entry<SubjectId, Member> entry : members.entrySet()) {
            Member current = entry.getValue(); Member candidate = Objects.requireNonNull(nextMembers.get(entry.getKey()), "next assembly member");
            if (!current.corridor().equals(candidate.corridor()) || candidate.cursor() < current.cursor()
                    || candidate.cursor() > current.nextColdCursor()) {
                throw new IllegalArgumentException("assembly member must advance its existing bounded corridor");
            }
            if (candidate.cursor() > current.cursor()) {
                if (advancedActor != null && deferral.isPresent()) {
                    throw new IllegalArgumentException("loaded-world assembly deferral permits only its blocked member to advance");
                }
                advancedActor = entry.getKey();
            }
            next.put(entry.getKey(), candidate);
        }
        if (advancedActor == null) throw new IllegalArgumentException("assembly advance must move at least one member");
        if (deferral.isPresent()) {
            OperationAssemblyDeferral blocked = deferral.orElseThrow();
            Member prior = members.get(blocked.actorId()), advanced = next.get(blocked.actorId());
            if (!blocked.actorId().equals(advancedActor) || advanced.cursor() != prior.cursor() + 1
                    || !advanced.currentPosition().equals(blocked.target())) {
                throw new IllegalArgumentException("loaded-world assembly deferral may clear only through its blocked exact next cursor");
            }
        }
        return new OperationAssembly(next, cargoCarrierId);
    }

    public OperationAssembly defer(OperationAssemblyDeferral nextDeferral) {
        nextDeferral = Objects.requireNonNull(nextDeferral, "assembly deferral");
        if (deferral.isPresent() && !deferral.orElseThrow().equals(nextDeferral)) {
            throw new IllegalArgumentException("assembly already has a different loaded-world deferral");
        }
        return new OperationAssembly(members, cargoCarrierId, Optional.of(nextDeferral));
    }

    public record Member(List<BlockPosition> corridor, int cursor) {
        public Member {
            corridor = List.copyOf(corridor);
            if (corridor.isEmpty() || corridor.size() > OperationTravel.MAX_CELLS) throw new IllegalArgumentException("assembly corridor must contain 1.." + OperationTravel.MAX_CELLS + " cells");
            for (int index = 1; index < corridor.size(); index++) {
                BlockPosition prior = Objects.requireNonNull(corridor.get(index - 1), "assembly cell"); BlockPosition next = Objects.requireNonNull(corridor.get(index), "assembly cell");
                if (prior.y() != next.y() || Math.abs(prior.x() - next.x()) + Math.abs(prior.z() - next.z()) != 1) throw new IllegalArgumentException("assembly corridor must be adjacent");
            }
            if (cursor < 0 || cursor >= corridor.size()) throw new IllegalArgumentException("assembly cursor is outside corridor");
        }
        public BlockPosition currentPosition() { return corridor.get(cursor); }
        public BlockPosition destination() { return corridor.getLast(); }
        public boolean arrived() { return cursor == corridor.size() - 1; }
        public int nextColdCursor() { return Math.min(cursor + MAX_COLD_ADVANCE, corridor.size() - 1); }
    }
}
