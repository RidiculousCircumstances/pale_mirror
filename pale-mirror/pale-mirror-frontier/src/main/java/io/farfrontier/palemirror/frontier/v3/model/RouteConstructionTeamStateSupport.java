package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Aggregate invariant for exact construction crews and every competing human owner. */
final class RouteConstructionTeamStateSupport {
    private RouteConstructionTeamStateSupport() { }

    static void validate(HumanPopulation population, Map<SubjectId, RouteConstruction> constructions,
                         Map<SubjectId, RouteMaintenance> maintenances,
                         Map<SubjectId, ProductionJob> jobs, ResourceSiteState sites,
                         Map<SubjectId, RouteOperation> operations, Map<SubjectId, SupplyContract> contracts,
                         StrategicPlanState plans) {
        Set<SubjectId> assigned = new HashSet<>();
        jobs.values().forEach(job -> assigned.add(job.workerId()));
        sites.sites().values().stream().filter(site -> site.phase() == ResourceSitePhase.HARVESTING).map(ResourceSiteLifecycle::activeWork).flatMap(java.util.Optional::stream)
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .forEach(job -> assigned.add(job.workerId()));
        operations.values().stream().filter(operation -> FrontierWorldStateSupport.retainsParticipantClaim(contracts, operation))
                .forEach(operation -> assigned.addAll(operation.participantIds()));
        plans.routePatrols().values().stream().filter(RoutePatrol::active)
                .forEach(patrol -> assigned.addAll(patrol.memberIds()));
        plans.settlementAssaults().values().stream().filter(assault -> assault.status() != SettlementAssaultStatus.RESOLVED)
                .forEach(assault -> assigned.addAll(assault.defenderIds()));
        population.migrations().keySet().forEach(assigned::add);
        population.medicalOperations().values().stream().filter(MedicalEvacuationOperation::active)
                .forEach(operation -> {
                    if (!assigned.add(operation.patientId())) throw new IllegalArgumentException("medical operation must retain an unassigned exact patient");
                    operation.team().memberIds().forEach(member -> {
                    if (!assigned.add(member)) throw new IllegalArgumentException("medical operation must retain distinct exact residents");
                    });
                });
        for (RouteConstruction project : constructions.values()) {
            if (project.team().isEmpty()) continue; // schema-83 historical autonomous work is preserved, never upgraded silently.
            EngineeringRecoveryTeam team = project.team().orElseThrow();
            for (SubjectId member : team.memberIds()) {
                ResidentProfile resident = population.resident(member);
                if (resident == null || !resident.settlementId().equals(project.settlementId()) || !assigned.add(member)) {
                    throw new IllegalArgumentException("route construction team must retain distinct local exact residents");
                }
            }
        }
        for (RouteMaintenance maintenance : maintenances.values()) {
            for (SubjectId member : maintenance.team().memberIds()) {
                ResidentProfile resident = population.resident(member);
                if (resident == null || !resident.settlementId().equals(maintenance.settlementId()) || !assigned.add(member)) {
                    throw new IllegalArgumentException("route maintenance team must retain distinct local exact residents");
                }
            }
        }
    }
}
