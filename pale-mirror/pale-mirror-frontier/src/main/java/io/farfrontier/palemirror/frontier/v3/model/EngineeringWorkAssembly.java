package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;

/**
 * Bounded approach of one exact engineering crew to declared service/work positions.
 *
 * <p>This is a movement record, not a scene and not a second crew roster. It retains the
 * same people already owned by {@link EngineeringRecoveryTeam}. HOT observes the same
 * checkpoints as COLD; departure retains an approach from the actual saved body position.</p>
 */
public record EngineeringWorkAssembly(EngineeringJourneyPurpose purpose, Map<SubjectId, Member> members) {
    public EngineeringWorkAssembly {
        purpose = Objects.requireNonNull(purpose, "engineering journey purpose");
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

    /** All currently unoccupied next cursors in canonical actor order. */
    public List<SubjectId> safeAdvances() {
        java.util.LinkedHashSet<SubjectId> movable = new java.util.LinkedHashSet<>();
        members.keySet().stream().sorted().forEach(actor -> {
            Member member = members.get(actor);
            if (!member.arrived() && members.entrySet().stream().noneMatch(entry -> !entry.getKey().equals(actor)
                    && entry.getValue().currentPosition().equals(member.nextSurface().support()))) {
                movable.add(actor);
            }
        });
        return movable.stream().sorted().toList();
    }

    /** One ordered COLD turn advances exactly one retained person by one existing corridor cell. */
    public EngineeringWorkAssembly advance(SubjectId actorId) {
        SubjectId actor = Objects.requireNonNull(actorId, "engineering assembly advance actor");
        Member current = members.get(actor);
        if (current == null || current.arrived() || !safeAdvances().contains(actor)) throw new IllegalArgumentException("engineering assembly actor cannot safely advance");
        Map<SubjectId, Member> next = new LinkedHashMap<>(members);
        next.put(actor, current.advanceOne());
        return new EngineeringWorkAssembly(purpose, next);
    }

    /** Departure changes only the exact member's spatial continuation, never its semantic route. */
    public EngineeringWorkAssembly checkpoint(SubjectId actor, TraversalRejoin approach) {
        Member current = members.get(actor);
        if (current == null) throw new IllegalArgumentException("engineering checkpoint has no exact member");
        Map<SubjectId, Member> next = new LinkedHashMap<>(members);
        next.put(actor, current.withRejoin(approach));
        return new EngineeringWorkAssembly(purpose, next);
    }

    public record Member(List<BlockPosition> corridor, int cursor, long routeRevision, Optional<TraversalRejoin> rejoin) {
        public Member(List<BlockPosition> corridor, int cursor) { this(corridor, cursor, 1L, Optional.empty()); }
        public Member {
            corridor = List.copyOf(Objects.requireNonNull(corridor, "engineering assembly corridor"));
            if (corridor.isEmpty() || corridor.size() > OperationTravel.MAX_CELLS || cursor < 0 || cursor >= corridor.size()) {
                throw new IllegalArgumentException("engineering assembly corridor/cursor is out of bounds");
            }
            for (int index = 1; index < corridor.size(); index++) {
                BlockPosition prior = Objects.requireNonNull(corridor.get(index - 1), "engineering assembly corridor cell");
                BlockPosition next = Objects.requireNonNull(corridor.get(index), "engineering assembly corridor cell");
                if (Math.abs(prior.y() - next.y()) > 1 || Math.abs(prior.x() - next.x()) + Math.abs(prior.z() - next.z()) != 1) {
                    throw new IllegalArgumentException("engineering assembly corridor must use adjacent supports with grade at most one");
                }
            }
            if (routeRevision < 1) throw new IllegalArgumentException("engineering route requires a positive revision");
            Objects.requireNonNull(rejoin, "engineering rejoin");
            if (rejoin.isPresent() && !rejoin.orElseThrow().target().equals(new SurfaceAnchor(
                    corridor.get(Math.min(cursor + 1, corridor.size() - 1)))))
                throw new IllegalArgumentException("engineering rejoin changes its next semantic checkpoint");
        }
        public SurfaceAnchor currentSurface() { return rejoin.map(TraversalRejoin::current).orElseGet(() -> new SurfaceAnchor(corridor.get(cursor))); }
        public BlockPosition currentPosition() { return currentSurface().support(); }
        public SurfaceAnchor nextSurface() {
            if (arrived()) throw new IllegalArgumentException("arrived engineer has no next step");
            return rejoin.map(value -> value.path().get(value.nextCursor(1))).orElseGet(() -> new SurfaceAnchor(corridor.get(cursor + 1)));
        }
        public BlockPosition destination() { return corridor.getLast(); }
        public boolean arrived() { return cursor == corridor.size() - 1 && rejoin.isEmpty(); }
        public Member withRejoin(TraversalRejoin approach) {
            return new Member(corridor, cursor, Math.incrementExact(routeRevision), Optional.of(approach));
        }
        public Member advanceOne() {
            if (arrived()) throw new IllegalArgumentException("complete engineering member cannot advance");
            if (rejoin.isEmpty()) return new Member(corridor, cursor + 1, routeRevision, Optional.empty());
            var approach = rejoin.orElseThrow();
            var advanced = approach.arrived() ? approach : approach.advance(approach.nextCursor(1), 1);
            return advanced.arrived() ? new Member(corridor, Math.min(cursor + 1, corridor.size() - 1), routeRevision, Optional.empty())
                    : new Member(corridor, cursor, routeRevision, Optional.of(advanced));
        }
    }
}
