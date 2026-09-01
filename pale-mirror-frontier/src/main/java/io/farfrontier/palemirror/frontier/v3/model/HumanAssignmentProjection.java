package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical read model for exclusive current human work.  It introduces no
 * second persisted truth: every non-idle value is compiled from its owning job,
 * operation, patrol or Transit journey.
 */
public record HumanAssignmentProjection(Map<SubjectId, HumanAssignment> assignments) {
    public HumanAssignmentProjection {
        assignments = Map.copyOf(Objects.requireNonNull(assignments, "human assignments"));
        assignments.forEach((resident, assignment) -> {
            if (!resident.equals(assignment.residentId())) throw new IllegalArgumentException("assignment map key must match resident");
        });
    }

    public static HumanAssignmentProjection compile(FrontierWorldState state) {
        Objects.requireNonNull(state, "assignment state");
        Map<SubjectId, HumanAssignment> values = new LinkedHashMap<>();
        state.humanPopulation().residentIds().stream().sorted().forEach(id -> values.put(id, HumanAssignment.idle(id)));
        state.productionJobs().values().stream().sorted(Comparator.comparing(ProductionJob::id))
                .forEach(job -> claim(values, job.workerId(), HumanAssignmentKind.INDUSTRIAL_WORK, job.id()));
        state.resourceSites().sites().values().stream().sorted(Comparator.comparing(ResourceSiteLifecycle::siteId))
                .map(ResourceSiteLifecycle::activeWork).flatMap(java.util.Optional::stream)
                .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                .forEach(job -> claim(values, job.workerId(), HumanAssignmentKind.FIELD_HARVEST, job.id()));
        state.operations().values().stream().sorted(Comparator.comparing(RouteOperation::id))
                .filter(operation -> FrontierWorldStateSupport.retainsParticipantClaim(state, operation)).forEach(operation -> {
                    operation.unit().members().forEach(member -> claim(values, member.residentId(),
                            member.duty() == RouteUnitDuty.CARGO_CREW ? HumanAssignmentKind.CARGO_TRANSPORT : HumanAssignmentKind.ESCORT, operation.id()));
                });
        state.strategicPlans().routePatrols().values().stream().sorted(Comparator.comparing(RoutePatrol::taskId))
                .filter(patrol -> patrol.status() == RoutePatrolStatus.EN_ROUTE)
                .forEach(patrol -> patrol.memberIds().forEach(member -> claim(values, member, HumanAssignmentKind.ROUTE_PATROL, patrol.taskId())));
        state.strategicPlans().settlementAssaults().values().stream().sorted(Comparator.comparing(SettlementAssault::id))
                .filter(assault -> assault.status() != SettlementAssaultStatus.RESOLVED)
                .forEach(assault -> assault.defenderIds().forEach(id -> claim(values, id, HumanAssignmentKind.SETTLEMENT_DEFENCE, assault.id())));
        state.routeConstructions().values().stream().sorted(Comparator.comparing(RouteConstruction::id))
                .filter(project -> project.status() != RouteConstructionStatus.CONFLICT)
                .flatMap(project -> project.team().stream())
                .forEach(team -> team.memberIds().forEach(id -> claim(values, id, HumanAssignmentKind.ENGINEERING_RECOVERY, team.ownerId())));
        state.humanPopulation().medicalOperations().values().stream().sorted(Comparator.comparing(MedicalEvacuationOperation::id))
                .filter(MedicalEvacuationOperation::active)
                .forEach(operation -> {
                    claim(values, operation.patientId(), HumanAssignmentKind.MEDICAL_EVACUATION, operation.id());
                    operation.team().memberIds().forEach(id -> claim(values, id, HumanAssignmentKind.MEDICAL_EVACUATION, operation.id()));
                });
        state.humanPopulation().migrations().values().stream().sorted(Comparator.comparing(ResidentMigrationJourney::residentId))
                .forEach(journey -> claim(values, journey.residentId(), HumanAssignmentKind.TRANSIT, journey.residentId()));
        return new HumanAssignmentProjection(values);
    }

    public HumanAssignment assignment(SubjectId residentId) {
        HumanAssignment assignment = assignments.get(Objects.requireNonNull(residentId, "assignment resident"));
        if (assignment == null) throw new IllegalArgumentException("unknown assignment resident");
        return assignment;
    }

    public boolean idle(SubjectId residentId) { return !assignment(residentId).active(); }

    private static void claim(Map<SubjectId, HumanAssignment> values, SubjectId residentId, HumanAssignmentKind kind, SubjectId ownerId) {
        HumanAssignment current = values.get(residentId);
        if (current == null) throw new IllegalArgumentException("human assignment source names an unknown resident");
        if (current.active()) throw new IllegalArgumentException("resident has conflicting active assignments: " + residentId.value());
        values.put(residentId, new HumanAssignment(residentId, kind, java.util.Optional.of(ownerId)));
    }
}
