package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryWork;

/**
 * Canonical read model for exclusive retained human work. It introduces no
 * second persisted truth: every non-idle value is compiled from its owning job,
 * operation, patrol or Transit journey.
 */
public record HumanAssignmentProjection(Map<SubjectId, HumanAssignment> assignments) {
    private static final int MAX_CACHED_REVISIONS = 128;
    private static final BoundedValueMemo<Dependencies, HumanAssignmentProjection> CACHE =
            new BoundedValueMemo<>(MAX_CACHED_REVISIONS);
    private record Current(FrontierWorldState state, HumanAssignmentProjection projection) { }
    private static final ThreadLocal<Current> CURRENT = new ThreadLocal<>();
    private static final ImmutableRegistryView<SubjectId, ResourceSiteLifecycle, List<HumanAssignment>> FIELD_ROWS =
            new ImmutableRegistryView<>(ResourceSiteState.MAX_SITES, Objects::equals);
    private static final ImmutableInputView<Map<SubjectId, List<HumanAssignment>>> FIELDS = new ImmutableInputView<>();
    private static Map<SubjectId, List<HumanAssignment>> fieldAssignments(FrontierWorldState state) {
        return FIELDS.get(List.of(state.bootstrap(), state.resourceSites().sites()), () -> FIELD_ROWS.project(
                state.bootstrap(), state.resourceSites().sites(),
                (id, field) -> field.harvestJobs().values().stream().map(job -> new HumanAssignment(
                        job.workerId(), HumanAssignmentKind.FIELD_HARVEST, java.util.Optional.of(job.id()))).toList()));
    }
    private static final ImmutableRegistryView<SubjectId, ProductionJob, Optional<HumanAssignment>> PRODUCTION =
            new ImmutableRegistryView<>(SimulationCapacity.MAX_RESIDENTS, Objects::equals);
    private static final ImmutableRegistryView<SubjectId, ExtractionWork, Optional<HumanAssignment>> EXTRACTION =
            new ImmutableRegistryView<>(ExtractionSiteState.MAX_WORK, Objects::equals);
    private static final ImmutableRegistryView<SubjectId, ForestryWork, Optional<HumanAssignment>> FORESTRY =
            new ImmutableRegistryView<>(SimulationCapacity.MAX_RESIDENTS, Objects::equals);
    private static final ImmutableRegistryView<SubjectId, Shipment, Optional<HumanAssignment>> COURIERS =
            new ImmutableRegistryView<>(ShipmentState.MAX_SHIPMENTS, Objects::equals);
    private static Optional<HumanAssignment> assignment(SubjectId actor, HumanAssignmentKind kind, SubjectId owner) {
        return Optional.of(new HumanAssignment(actor, kind, Optional.of(owner)));
    }
    private static Map<SubjectId, Optional<HumanAssignment>> production(FrontierWorldState state) {
        return PRODUCTION.project(state.bootstrap(), state.productionJobs(),
                (id, job) -> assignment(job.workerId(), HumanAssignmentKind.PRODUCTION, job.id()));
    }
    private static Map<SubjectId, Optional<HumanAssignment>> extraction(FrontierWorldState state) {
        return EXTRACTION.project(state.bootstrap(), state.extractionSites().work(), (id, job) -> job.terminal()
                ? Optional.empty() : assignment(job.execution().actorId(), HumanAssignmentKind.EXTRACTION, job.id()));
    }
    private static Map<SubjectId, Optional<HumanAssignment>> forestry(FrontierWorldState state) {
        return FORESTRY.project(state.bootstrap(), state.forest().work(),
                (id, job) -> assignment(job.execution().actorId(), HumanAssignmentKind.FORESTRY, job.id()));
    }
    private static Map<SubjectId, Optional<HumanAssignment>> couriers(FrontierWorldState state) {
        return COURIERS.project(state.bootstrap(), state.shipments().shipments(), (id, shipment) ->
                shipment.terminal() || TransportCargoExecution.shared(shipment) ? Optional.empty()
                        : assignment(shipment.execution().actorId(), HumanAssignmentKind.COURIER, shipment.id()));
    }
    private static void claimProjected(Map<SubjectId, HumanAssignment> target, Map<SubjectId, Optional<HumanAssignment>> rows) {
        rows.values().stream().flatMap(Optional::stream).sorted(Comparator.comparing(value -> value.ownerId().orElseThrow()))
                .forEach(value -> claim(target, value.residentId(), value.kind(), value.ownerId().orElseThrow()));
    }
    private static final ImmutableRegistryView<SubjectId, UnitGroup, List<UnitGroup.Member>> GROUP_ROWS =
            new ImmutableRegistryView<>(UnitGroupState.MAX_GROUPS, Objects::equals);
    private static final ImmutableInputView<Map<SubjectId, List<UnitGroup.Member>>> GROUPS = new ImmutableInputView<>();
    private static Map<SubjectId, List<UnitGroup.Member>> groupMembers(FrontierWorldState state) {
        return GROUPS.get(List.of(state.bootstrap(), state.unitGroups().groups()), () -> GROUP_ROWS.project(
                state.bootstrap(), state.unitGroups().groups(), (id, group) ->
                        group.phase() == UnitGroup.Phase.CLOSED ? List.of() : group.members()));
    }
    /** Identity comparisons of exactly the immutable sources read by compileFresh; no world-state hashing. */
    private record Dependencies(List<Object> sources) {
        static Dependencies of(FrontierWorldState state) {
            return new Dependencies(List.of(state.humanPopulation().residents(), production(state),
                    fieldAssignments(state), state.strategicPlans().routePatrols(),
                    state.strategicPlans().settlementAssaults(), state.routeConstructions(), state.routeMaintenances(),
                    state.serviceWorks(), state.humanPopulation().medicalOperations(), state.humanPopulation().migrations(),
                    couriers(state), groupMembers(state), ActorIdentityDeclarations.declaredKinds(state.bootstrap(), state.actorLocations()),
                    extraction(state), forestry(state), state.strategicPlans().guards(),state.humanPopulation().clinical().tasks()));
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Dependencies value) || sources.size() != value.sources.size()) return false;
            for (int index = 0; index < sources.size(); index++) if (sources.get(index) != value.sources.get(index)) return false;
            return true;
        }
        @Override public int hashCode() {
            int hash = 1;
            for (Object source : sources) hash = 31 * hash + System.identityHashCode(source);
            return hash;
        }
    }
    public HumanAssignmentProjection {
        assignments = ImmutableLookupMap.copyOf(Objects.requireNonNull(assignments, "human assignments"));
        assignments.forEach((resident, assignment) -> {
            if (!resident.equals(assignment.residentId())) throw new IllegalArgumentException("assignment map key must match resident");
        });
    }

    public static HumanAssignmentProjection compile(FrontierWorldState state) {
        Objects.requireNonNull(state, "assignment state");
        var current = CURRENT.get();
        if (current != null && current.state() == state) return current.projection();
        // Both the dependency projection and a cache miss are pure snapshot
        // computations. Do not serialize server/advisory workers while they scan
        // independent populations; the shared memo locks only lookup/publication.
        var compiled = CACHE.get(Dependencies.of(state), () -> compileFresh(state));
        CURRENT.set(new Current(state, compiled));
        return compiled;
    }

    private static HumanAssignmentProjection compileFresh(FrontierWorldState state) {
        Map<SubjectId, HumanAssignment> values = new LinkedHashMap<>();
        state.humanPopulation().residentIds().stream().sorted().forEach(id -> values.put(id, HumanAssignment.idle(id)));
        claimProjected(values, production(state));
        state.strategicPlans().settlementAssaults().values().stream().sorted(Comparator.comparing(SettlementAssault::id))
                .flatMap(parent -> parent.equipmentReturnVisit().stream()).filter(visit -> visit.execution().isPresent())
                .forEach(visit -> claim(values, visit.actorId(), HumanAssignmentKind.EQUIPMENT_SERVICE, visit.ownerId()));
        claimProjected(values, extraction(state));
        claimProjected(values, forestry(state));
        state.strategicPlans().guards().values().stream().sorted(Comparator.comparing(GuardDuty::id))
                .forEach(duty -> claim(values, duty.execution().actorId(), HumanAssignmentKind.GUARD, duty.id()));
        state.humanPopulation().clinical().tasks().values().stream().filter(t->t.role()==io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalTask.Role.MEDIC)
                .sorted(Comparator.comparing(io.farfrontier.palemirror.frontier.v3.model.clinical.ClinicalTask::id))
                .forEach(t->claim(values,t.actor(),HumanAssignmentKind.CLINICAL_CARE,t.id()));
        // A conflict suspends execution, not the retained job's exclusive worker claim.
        fieldAssignments(state).entrySet().stream().sorted(Map.Entry.comparingByKey())
                .flatMap(site -> site.getValue().stream())
                .forEach(assignment -> claim(values, assignment.residentId(), assignment.kind(), assignment.ownerId().orElseThrow()));
        state.strategicPlans().routePatrols().values().stream().sorted(Comparator.comparing(RoutePatrol::taskId))
                .filter(RoutePatrol::active)
                .forEach(patrol -> patrol.memberIds().forEach(member -> claim(values, member, HumanAssignmentKind.ROUTE_PATROL, patrol.taskId())));
        state.strategicPlans().settlementAssaults().values().stream().sorted(Comparator.comparing(SettlementAssault::id))
                .filter(assault -> assault.status() != SettlementAssaultStatus.RESOLVED)
                .forEach(assault -> assault.defenderIds().forEach(id -> claim(values, id, HumanAssignmentKind.SETTLEMENT_DEFENCE, assault.id())));
        state.routeConstructions().values().stream().sorted(Comparator.comparing(RouteConstruction::id))
                .filter(project -> project.status() != RouteConstructionStatus.CONFLICT)
                .flatMap(project -> project.team().stream())
                .forEach(team -> team.memberIds().forEach(id -> claim(values, id, HumanAssignmentKind.ENGINEERING_RECOVERY, team.ownerId())));
        state.routeMaintenances().values().stream().sorted(Comparator.comparing(RouteMaintenance::id))
                .filter(maintenance -> maintenance.status() != RouteMaintenanceStatus.CONFLICT)
                .forEach(maintenance -> maintenance.team().memberIds().forEach(id -> claim(values, id,
                        HumanAssignmentKind.ENGINEERING_RECOVERY, maintenance.id())));
        state.serviceWorks().values().stream().sorted(Comparator.comparing(SettlementServiceWork::id))
                .filter(work -> work.phase().active())
                .forEach(work -> claim(values, work.workerId(), HumanAssignmentKind.SETTLEMENT_SERVICE, work.id()));
        state.humanPopulation().medicalOperations().values().stream().sorted(Comparator.comparing(MedicalEvacuationOperation::id))
                .filter(MedicalEvacuationOperation::active)
                .forEach(operation -> {
                    claim(values, operation.patientId(), HumanAssignmentKind.MEDICAL_EVACUATION, operation.id());
                    operation.team().memberIds().forEach(id -> claim(values, id, HumanAssignmentKind.MEDICAL_EVACUATION, operation.id()));
                });
        state.humanPopulation().migrations().values().stream().sorted(Comparator.comparing(ResidentMigrationJourney::residentId))
                .forEach(journey -> claim(values, journey.residentId(), HumanAssignmentKind.TRANSIT, journey.residentId()));
        var courierRows = new LinkedHashMap<SubjectId, Optional<HumanAssignment>>();
        couriers(state).forEach((id, assignment) -> {
            if (assignment.filter(value -> state.actorLocations().get(value.residentId()).kind() == ActorKind.RESIDENT).isPresent())
                courierRows.put(id, assignment);
        });
        claimProjected(values, courierRows);
        for (var group : groupMembers(state).entrySet())
            for (var member : group.getValue()) {
                if (state.actorLocations().get(member.actorId()).kind() != ActorKind.RESIDENT) continue;
                var assigned = values.get(member.actorId());
                if (assigned != null && assigned.kind() == HumanAssignmentKind.COURIER
                        && assigned.ownerId().equals(java.util.Optional.of(member.activityOwnerId()))) continue;
                claim(values, member.actorId(), HumanAssignmentKind.GROUP_MEMBER, group.getKey());
            }
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
