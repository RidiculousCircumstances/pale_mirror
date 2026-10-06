package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A domain roster and coordinated journey, not another execution/body/resource registry. */
public record UnitGroup(SubjectId id, Mission mission, List<Member> members, Formation formation,
                        Phase phase, long revision, long goalOrdinal, Optional<Journey> journey) {
    public static final int MAX_MEMBERS = 32;
    public enum MissionKind { TRANSPORT }
    public enum Role { CARRIER, ESCORT, GUIDE }
    public enum Formation { COLUMN }
    public enum Phase { READY, TRAVELLING, AT_GOAL, CLOSED }
    public record Mission(MissionKind kind, SubjectId id) {
        public Mission { Objects.requireNonNull(kind); Objects.requireNonNull(id); }
    }
    public record Member(SubjectId actorId, Role role, ActorActivityKind activityKind, SubjectId activityOwnerId) {
        public Member {
            Objects.requireNonNull(actorId); Objects.requireNonNull(role);
            Objects.requireNonNull(activityKind); Objects.requireNonNull(activityOwnerId);
        }
    }
    public record Journey(SurfaceAnchor destination, List<SurfaceAnchor> route, int cursor,
                          Map<SubjectId, SurfaceAnchor> stations) {
        public Journey {
            Objects.requireNonNull(destination); route = List.copyOf(route); stations = Map.copyOf(stations);
            if (route.size() > 1) new PedestrianRouteReceipt(route);
            if (route.isEmpty() || cursor < 0 || cursor >= route.size() || stations.isEmpty()
                    || stations.values().stream().distinct().count() != stations.size())
                throw new IllegalArgumentException("group journey needs a bounded route and distinct current stations");
        }
    }
    public UnitGroup {
        Objects.requireNonNull(id); Objects.requireNonNull(mission); Objects.requireNonNull(formation);
        Objects.requireNonNull(phase); journey = Objects.requireNonNull(journey); members = List.copyOf(members);
        if (members.isEmpty() || members.size() > MAX_MEMBERS || revision < 1 || goalOrdinal < 0
                || phase == Phase.READY && goalOrdinal != 0 || phase != Phase.READY && goalOrdinal == 0
                || members.stream().map(Member::actorId).distinct().count() != members.size()
                || (phase == Phase.TRAVELLING) != journey.isPresent()
                || journey.isPresent() && !journey.orElseThrow().stations().keySet().equals(
                    members.stream().map(Member::actorId).collect(java.util.stream.Collectors.toUnmodifiableSet())))
            throw new IllegalArgumentException("group requires exact distinct roster, mission and legal journey phase");
    }
    public Member member(SubjectId actor) {
        return members.stream().filter(value -> value.actorId().equals(actor)).findFirst().orElseThrow(
                () -> new IllegalArgumentException("actor is outside the declared group roster"));
    }
    public UnitGroup start(long ordinal, Journey next) {
        boolean continuation = phase == Phase.TRAVELLING && ordinal == goalOrdinal
                && next.destination().equals(journey.orElseThrow().destination());
        if (phase == Phase.CLOSED || !continuation && (phase == Phase.TRAVELLING || ordinal <= goalOrdinal))
            throw new IllegalArgumentException("group cannot overwrite an active or obsolete goal");
        return new UnitGroup(id, mission, members, formation, Phase.TRAVELLING, revision + 1, ordinal, Optional.of(next));
    }
    public UnitGroup frame(Journey next) {
        var previous = journey.orElseThrow();
        if (phase != Phase.TRAVELLING || !next.destination().equals(previous.destination())
                || !next.route().equals(previous.route()) || next.cursor() <= previous.cursor())
            throw new IllegalArgumentException("group frame cannot replace its route or regress progress");
        return new UnitGroup(id, mission, members, formation, phase, revision + 1, goalOrdinal, Optional.of(next));
    }
    public UnitGroup arrived() {
        if (phase != Phase.TRAVELLING || journey.orElseThrow().cursor() != journey.orElseThrow().route().size() - 1
                || !journey.orElseThrow().route().getLast().equals(journey.orElseThrow().destination()))
            throw new IllegalArgumentException("group checkpoint is not its semantic destination");
        return new UnitGroup(id, mission, members, formation, Phase.AT_GOAL, revision + 1, goalOrdinal, Optional.empty());
    }
    public UnitGroup close() {
        if (phase != Phase.AT_GOAL) throw new IllegalArgumentException("group must settle its journey before closing");
        return new UnitGroup(id, mission, members, formation, Phase.CLOSED, revision + 1, goalOrdinal, Optional.empty());
    }
}
