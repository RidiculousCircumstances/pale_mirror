package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Narrow transition audit for planner/health-only updates; it owns no canonical state. */
final class FrontierPlannerHealthValidation {
    private FrontierPlannerHealthValidation() { }

    static void validate(FrontierBootstrap bootstrap, HumanPopulation population, StrategicPlanState plans,
                         RouteTopology topology, HiveColony colony, Map<SubjectId, ActorLocation> actors,
                         Map<SubjectId, RouteOperation> operations, Map<SubjectId, SupplyContract> contracts,
                         FencedRecoveryState bodyRecovery) {
        Set<SubjectId> settlements = bootstrap.settlements().stream().map(Settlement::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!population.quarantines().keySet().equals(settlements)) {
            throw new IllegalArgumentException("settlement quarantine index must own every and only canonical settlement");
        }
        plans.validate(bootstrap, topology, population);
        validateActorClaims(population, plans, operations, contracts);
        FrontierRouteEngagementSupport.validate(bootstrap, colony, actors, operations, plans, bodyRecovery);
        FrontierSettlementAssaultSupport.validate(bootstrap, colony, population, actors, plans);
    }

    /** Exact COLD authority remains exclusive even when only a route patrol plan has changed. */
    private static void validateActorClaims(HumanPopulation population, StrategicPlanState plans,
                                            Map<SubjectId, RouteOperation> operations, Map<SubjectId, SupplyContract> contracts) {
        Set<SubjectId> operationParticipants = new HashSet<>();
        for (RouteOperation operation : operations.values()) {
            if (!FrontierWorldStateSupport.retainsParticipantClaim(contracts, operation)) continue;
            for (SubjectId participant : operation.participantIds()) {
                if (!operationParticipants.add(participant)) throw new IllegalArgumentException("resident cannot be assigned to multiple active route operations");
                boolean patrolClaim = plans.routePatrols().values().stream()
                        .anyMatch(patrol -> patrol.active() && patrol.memberIds().contains(participant));
                if (population.migrations().containsKey(participant) || patrolClaim) {
                    throw new IllegalArgumentException("active route operation participant cannot retain a competing migration or patrol claim");
                }
            }
        }
        for (ResidentMigrationJourney journey : population.migrations().values()) {
            boolean patrolClaim = plans.routePatrols().values().stream()
                    .anyMatch(patrol -> patrol.active() && patrol.memberIds().contains(journey.residentId()));
            if (patrolClaim) throw new IllegalArgumentException("migration journey resident cannot retain a competing operation or patrol claim");
        }
    }
}
