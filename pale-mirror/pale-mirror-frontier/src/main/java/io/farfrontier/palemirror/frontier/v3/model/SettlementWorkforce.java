package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Comparator;
import java.util.List;

/** Shared participant selection; retained work remains the sole canonical assignment store. */
public final class SettlementWorkforce {
    private SettlementWorkforce() { }
    public static List<ResidentProfile> candidates(FrontierWorldState state, SubjectId settlementId,
                                                   ResidentWorkKind work, HumanCapability capability) {
        var permissions = SettlementWorkPolicy.permissions(state, settlementId);
        var assignments = HumanAssignmentProjection.compile(state);
        return permissions.workers(work).stream().map(id -> {
                    ResidentProfile resident = state.humanPopulation().resident(id);
                    if (resident == null || !resident.settlementId().equals(settlementId))
                        throw new IllegalArgumentException("work authorization references a missing or foreign resident");
                    return resident;
                })
                .filter(resident -> resident.capability(capability) > 0
                        && availableForNewAssignment(state, resident) && assignments.idle(resident.id()))
                .sorted(Comparator.comparingInt((ResidentProfile resident) -> permissions.priority(work, resident.id()))
                        .thenComparing(Comparator.comparingInt((ResidentProfile resident) -> resident.capability(capability)).reversed())
                        .thenComparing(ResidentProfile::id)).toList();
    }
    public static List<ResidentProfile> candidates(FrontierWorldState state, SubjectId settlementId,
                                                    HumanCapability capability) {
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        return state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId) && resident.capability(capability) > 0)
                .filter(resident -> availableForNewAssignment(state, resident))
                .filter(resident -> assignments.idle(resident.id()))
                .sorted(Comparator.comparingInt((ResidentProfile resident) ->
                        resident.capability(capability)).reversed().thenComparing(ResidentProfile::id))
                .toList();
    }

    /** A retained owner is not released when its resident temporarily selects food or FREE time. */
    public static void requireAvailable(FrontierWorldState state, SubjectId settlementId, List<SubjectId> participants) {
        if (participants.isEmpty() || participants.size() > 64 || participants.stream().distinct().count() != participants.size())
            throw new IllegalArgumentException("participant request must be bounded and unique");
        var assignments = HumanAssignmentProjection.compile(state);
        for (SubjectId id : participants) {
            ResidentProfile resident = state.humanPopulation().resident(id);
            if (resident == null || !resident.settlementId().equals(settlementId)
                    || !availableForNewAssignment(state, resident) || !assignments.idle(id))
                throw new IllegalArgumentException("participant is unavailable to settlement: " + id.value());
        }
    }
    public static boolean availableForNewAssignment(FrontierWorldState state, ResidentProfile resident) {
        ActorLocation actor = state.actorLocations().get(resident.id());
        return actor != null && actor.condition().status() == ActorLifeStatus.ALIVE
                && !state.humanPopulation().meals().containsKey(resident.id())
                && !state.actorMovements().containsKey(resident.id());
    }
}
