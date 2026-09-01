package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Bounded COLD approach of one exact engineering crew to declared work-site positions.
 *
 * <p>This is a movement record, not a scene and not a second crew roster. It retains the
 * same people already owned by {@link EngineeringRecoveryTeam}; a future HOT lease may begin
 * only after every one of these immutable corridors reaches its terminal position.</p>
 */
public record EngineeringWorkAssembly(Map<SubjectId, Member> members) {
    public EngineeringWorkAssembly {
        Objects.requireNonNull(members, "engineering assembly members");
        Map<SubjectId, Member> copy = new LinkedHashMap<>();
        members.forEach((actor, member) -> {
            if (copy.put(Objects.requireNonNull(actor, "engineering assembly actor"), Objects.requireNonNull(member, "engineering assembly member")) != null) {
                throw new IllegalArgumentException("engineering assembly has duplicate member");
            }
        });
        if (copy.size() < EngineeringRecoveryTeam.MIN_MEMBERS || copy.size() > EngineeringRecoveryTeam.MAX_MEMBERS
                || copy.values().stream().map(Member::currentPosition).distinct().count() != copy.size()
                || copy.values().stream().map(Member::destination).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("engineering assembly must retain distinct exact member positions");
        }
        members = Map.copyOf(copy);
    }

    public boolean complete() { return members.values().stream().allMatch(Member::arrived); }

    public Map<SubjectId, BlockPosition> positions() {
        Map<SubjectId, BlockPosition> positions = new LinkedHashMap<>();
        members.forEach((actor, member) -> positions.put(actor, member.currentPosition()));
        return Map.copyOf(positions);
    }

    /**
     * Selects the next deterministic collision-free COLD move. Corridors may cross, but a
     * retained team may never enter a cell occupied by another member in the same canonical
     * instant. The process deliberately waits when every next step is occupied rather than
     * manufacturing a swap or breaking exact-location causality.
     */
    public Optional<SubjectId> nextSafeAdvance() {
        return safeAdvances().stream().findFirst();
    }

    /** All currently safe moves in canonical actor order; callers may apply further ownership gates. */
    public List<SubjectId> safeAdvances() {
        java.util.LinkedHashSet<SubjectId> movable = new java.util.LinkedHashSet<>();
        members.keySet().stream().sorted().forEach(actor -> advanceLeader(actor, new HashSet<>()).ifPresent(movable::add));
        return movable.stream().sorted().toList();
    }

    /** Resolves a queue from its empty leading cell backwards; cycles and arrived blockers stay COLD. */
    private Optional<SubjectId> advanceLeader(SubjectId actor, Set<SubjectId> visiting) {
        Member member = members.get(actor);
        if (member == null || member.arrived() || !visiting.add(actor)) return Optional.empty();
        BlockPosition next = member.corridor().get(member.cursor() + 1);
        SubjectId blocker = members.entrySet().stream().filter(entry -> !entry.getKey().equals(actor))
                .filter(entry -> entry.getValue().currentPosition().equals(next)).map(Map.Entry::getKey).findFirst().orElse(null);
        if (blocker == null) return Optional.of(actor);
        return advanceLeader(blocker, visiting);
    }

    /** One ordered COLD turn advances exactly one retained person by one existing corridor cell. */
    public EngineeringWorkAssembly advance(SubjectId actorId) {
        SubjectId actor = Objects.requireNonNull(actorId, "engineering assembly advance actor");
        Member current = members.get(actor);
        if (current == null || current.arrived()) throw new IllegalArgumentException("engineering assembly actor cannot advance");
        Map<SubjectId, Member> next = new LinkedHashMap<>(members);
        next.put(actor, new Member(current.corridor(), current.cursor() + 1));
        return new EngineeringWorkAssembly(next);
    }

    public record Member(List<BlockPosition> corridor, int cursor) {
        public Member {
            corridor = List.copyOf(Objects.requireNonNull(corridor, "engineering assembly corridor"));
            if (corridor.isEmpty() || corridor.size() > OperationTravel.MAX_CELLS || cursor < 0 || cursor >= corridor.size()) {
                throw new IllegalArgumentException("engineering assembly corridor/cursor is out of bounds");
            }
            for (int index = 1; index < corridor.size(); index++) {
                BlockPosition prior = Objects.requireNonNull(corridor.get(index - 1), "engineering assembly corridor cell");
                BlockPosition next = Objects.requireNonNull(corridor.get(index), "engineering assembly corridor cell");
                if (prior.y() != next.y() || Math.abs(prior.x() - next.x()) + Math.abs(prior.z() - next.z()) != 1) {
                    throw new IllegalArgumentException("engineering assembly corridor must remain horizontally adjacent");
                }
            }
        }
        public BlockPosition currentPosition() { return corridor.get(cursor); }
        public BlockPosition destination() { return corridor.getLast(); }
        public boolean arrived() { return cursor == corridor.size() - 1; }
    }
}
