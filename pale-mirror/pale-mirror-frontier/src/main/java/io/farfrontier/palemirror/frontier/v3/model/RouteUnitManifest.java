package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * Immutable exact human organization embedded in one route owner.
 *
 * <p>This is deliberately a value inside {@link RoutePatrol} or {@link RouteOperation},
 * not a global unit registry and not another assignment authority.  The legacy form exists
 * solely to preserve a route already persisted before the current composition contract.</p>
 */
public record RouteUnitManifest(SubjectId id, SubjectId ownerId, RouteUnitKind kind,
                                List<RouteUnitMember> members, SubjectId leaderId, boolean legacyUnderstrength) {
    public RouteUnitManifest {
        Objects.requireNonNull(id, "route unit id");
        Objects.requireNonNull(ownerId, "route unit owner");
        Objects.requireNonNull(kind, "route unit kind");
        members = List.copyOf(Objects.requireNonNull(members, "route unit members"));
        Objects.requireNonNull(leaderId, "route unit leader");
        if (!id.value().startsWith("unit:")) throw new IllegalArgumentException("route unit requires stable unit identity");
        if (members.isEmpty() || members.stream().map(RouteUnitMember::residentId).distinct().count() != members.size()) {
            throw new IllegalArgumentException("route unit members must be nonempty and distinct");
        }
        if (members.stream().noneMatch(member -> member.residentId().equals(leaderId))) {
            throw new IllegalArgumentException("route unit leader must remain an exact member");
        }
        validateComposition(kind, members, leaderId, legacyUnderstrength);
    }

    public static RouteUnitManifest patrol(SubjectId ownerId, SubjectId leaderId, List<SubjectId> scoutIds) {
        if (scoutIds.size() < 1 || scoutIds.size() > 3) throw new IllegalArgumentException("a new patrol needs one to three scouts");
        List<RouteUnitMember> members = new java.util.ArrayList<>();
        members.add(new RouteUnitMember(leaderId, RouteUnitDuty.PATROL_LEADER));
        scoutIds.forEach(id -> members.add(new RouteUnitMember(id, RouteUnitDuty.SCOUT)));
        return new RouteUnitManifest(idFor(RouteUnitKind.PATROL, ownerId), ownerId, RouteUnitKind.PATROL, members, leaderId, false);
    }

    public static RouteUnitManifest cargoEscort(SubjectId ownerId, SubjectId crewId, SubjectId leaderId, List<SubjectId> escortIds) {
        if (escortIds.size() < 2 || escortIds.size() > 4) throw new IllegalArgumentException("a new cargo operation needs two to four escorts");
        List<RouteUnitMember> members = new java.util.ArrayList<>();
        members.add(new RouteUnitMember(crewId, RouteUnitDuty.CARGO_CREW));
        escortIds.forEach(id -> members.add(new RouteUnitMember(id, RouteUnitDuty.ESCORT)));
        return new RouteUnitManifest(idFor(RouteUnitKind.CARGO_ESCORT, ownerId), ownerId, RouteUnitKind.CARGO_ESCORT, members, leaderId, false);
    }

    /** Exact historical shape; normal process admission must never call this factory. */
    public static RouteUnitManifest legacyPatrol(SubjectId ownerId, SubjectId guardId) {
        return new RouteUnitManifest(idFor(RouteUnitKind.PATROL, ownerId), ownerId, RouteUnitKind.PATROL,
                List.of(new RouteUnitMember(guardId, RouteUnitDuty.PATROL_LEADER)), guardId, true);
    }

    /** Exact historical shape; normal process admission must never call this factory. */
    public static RouteUnitManifest legacyCargoEscort(SubjectId ownerId, SubjectId crewId, SubjectId guardId) {
        return new RouteUnitManifest(idFor(RouteUnitKind.CARGO_ESCORT, ownerId), ownerId, RouteUnitKind.CARGO_ESCORT,
                List.of(new RouteUnitMember(crewId, RouteUnitDuty.CARGO_CREW), new RouteUnitMember(guardId, RouteUnitDuty.ESCORT)), guardId, true);
    }

    public List<SubjectId> memberIds() { return members.stream().map(RouteUnitMember::residentId).toList(); }
    public SubjectId cargoCrewId() {
        return members.stream().filter(member -> member.duty() == RouteUnitDuty.CARGO_CREW).map(RouteUnitMember::residentId)
                .findFirst().orElseThrow(() -> new IllegalStateException("non-cargo route unit has no cargo crew"));
    }

    public static SubjectId idFor(RouteUnitKind kind, SubjectId ownerId) {
        return new SubjectId("unit:" + kind.name().toLowerCase(java.util.Locale.ROOT) + "-" + ownerId.value().replace(':', '-'));
    }

    private static void validateComposition(RouteUnitKind kind, List<RouteUnitMember> members, SubjectId leaderId, boolean legacy) {
        long leaders = members.stream().filter(member -> member.duty() == RouteUnitDuty.PATROL_LEADER).count();
        long scouts = members.stream().filter(member -> member.duty() == RouteUnitDuty.SCOUT).count();
        long crew = members.stream().filter(member -> member.duty() == RouteUnitDuty.CARGO_CREW).count();
        long escorts = members.stream().filter(member -> member.duty() == RouteUnitDuty.ESCORT).count();
        switch (kind) {
            case PATROL -> {
                if (crew != 0 || escorts != 0 || leaders != 1 || (!legacy && (scouts < 1 || scouts > 3))
                        || (legacy && scouts != 0) || (!legacy && members.size() != scouts + 1)
                        || !members.stream().filter(member -> member.duty() == RouteUnitDuty.PATROL_LEADER).map(RouteUnitMember::residentId).findFirst().orElseThrow().equals(leaderId)) {
                    throw new IllegalArgumentException("invalid patrol unit composition");
                }
            }
            case CARGO_ESCORT -> {
                if (leaders != 0 || scouts != 0 || crew != 1 || (!legacy && (escorts < 2 || escorts > 4))
                        || (legacy && escorts != 1) || !members.stream().filter(member -> member.duty() == RouteUnitDuty.ESCORT)
                        .map(RouteUnitMember::residentId).anyMatch(leaderId::equals)) {
                    throw new IllegalArgumentException("invalid cargo escort unit composition");
                }
            }
        }
    }
}
