package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Canonical admission and validation for an engineering work-site scene. */
public final class FrontierEngineeringWorkSceneSupport {
    private FrontierEngineeringWorkSceneSupport() { }

    /**
     * A scene becomes possible only after the same retained crew has tools and completed its
     * immutable COLD approach. No materializer is allowed to create a crew or choose a cell.
     */
    public static Optional<EngineeringWorkSceneCandidate> candidate(FrontierWorldState state, EngineeringWorkOrder project) {
        if (!project.building() || project.engineeringTeam().isEmpty() || project.assembly().isEmpty()) return Optional.empty();
        EngineeringRecoveryTeam team = project.engineeringTeam().orElseThrow();
        EngineeringWorkAssembly assembly = project.assembly().orElseThrow();
        if (assembly.purpose() != EngineeringJourneyPurpose.WORKSITE || !assembly.complete() || !EngineeringToolCustody.ready(state, team)
                || project.confirmedCells() >= project.workCells().size()) return Optional.empty();
        Map<SubjectId, BlockPosition> positions = assembly.positions();
        if (!positions.keySet().equals(Set.copyOf(team.memberIds())) || positions.entrySet().stream()
                .anyMatch(entry -> !state.actorLocations().get(entry.getKey()).supportingSurface().support().equals(entry.getValue()))) return Optional.empty();
        return Optional.of(new EngineeringWorkSceneCandidate(project.id(), project.confirmedCells(),
                EngineeringWorksite.workCell(state.bootstrap(), state.routeTopology(), project), positions));
    }

    /** Complete stable worksite inventory; the adapter alone observes physical demand. */
    public static List<EngineeringWorkSceneCandidate> candidates(FrontierWorldState state) {
        return java.util.stream.Stream.concat(state.routeConstructions().values().stream(), state.routeMaintenances().values().stream())
                .sorted(Comparator.comparing(EngineeringWorkOrder::id))
                .map(project -> candidate(state, project)).flatMap(Optional::stream).toList();
    }

    public static EngineeringWorkOrder require(FrontierWorldState state, EngineeringWorkSceneCause cause) {
        EngineeringWorkOrder project = EngineeringWorkOrderSupport.require(state, cause.projectId());
        if (project.confirmedCells() != cause.workCellIndex()) throw new IllegalArgumentException("engineering scene cursor differs from current construction cell");
        return project;
    }

    public static SubjectId owner(FrontierWorldState state, EngineeringWorkSceneCause cause) {
        // A confirmed physical cell advances the construction cursor before its old HOT
        // work-site lease drains.  Owner resolution is intentionally broader than admission:
        // the old lease must still close or recover under the same project/settlement after
        // that advance, rather than being misread as a logistics scene or becoming ownerless.
        EngineeringWorkOrder project = EngineeringWorkOrderSupport.require(state, cause.projectId());
        return project.settlementId();
    }

    public static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        EngineeringWorkSceneCause cause = FrontierSceneBehaviors.engineeringWorksite(lease);
        EngineeringWorkOrder project = require(state, cause);
        EngineeringWorkSceneCandidate candidate = candidate(state, project)
                .orElseThrow(() -> new IllegalArgumentException("engineering scene has no exact COLD-ready crew"));
        if (!lease.handoffPosition().equals(candidate.workCell())
                || !lease.memberPositions().equals(SceneLease.bodiesAboveSupportCells(candidate.memberPositions()))
                || !lease.members().stream().map(SceneMember::actorId).collect(java.util.stream.Collectors.toSet())
                .equals(candidate.memberPositions().keySet())) {
            throw new IllegalArgumentException("engineering scene must retain the current exact crew and work cell");
        }
    }

    public static boolean owns(SceneLease lease, SubjectId subjectId) {
        return FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(subjectId);
    }

    /** A retained crew may open only its current-cell work intent while its exact lease is HOT. */
    public static boolean permitsCurrentWorkIntent(FrontierWorldState state, PhysicalIntent intent) {
        EngineeringWorkOrder project = intent.subjectIds().stream().map(id -> {
                    RouteConstruction construction = state.routeConstructions().get(id);
                    RouteMaintenance maintenance = state.routeMaintenances().get(id);
                    if (construction != null && maintenance != null) throw new IllegalArgumentException("engineering work owner is ambiguous");
                    return construction != null ? construction : maintenance;
                }).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (project == null || project.engineeringTeam().isEmpty() || project.cargoId().isEmpty()) return false;
        return state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isEngineeringWorksite)
                .filter(lease -> lease.status() == SceneLeaseStatus.HOT).anyMatch(lease -> {
                    EngineeringWorkSceneCause cause = FrontierSceneBehaviors.engineeringWorksite(lease);
                    return cause.projectId().equals(project.id()) && cause.workCellIndex() == project.confirmedCells()
                            && lease.handoffPosition().equals(EngineeringWorksite.workCell(state.bootstrap(), state.routeTopology(), project));
                });
    }

    /**
     * A work order owns its engineering-scene lifecycle. When a physical result makes that
     * order terminal, the exact HOT bodies must enter the normal release protocol in the same
     * canonical transition; otherwise they retain stale authority over a non-building cell.
     */
    public static Map<SceneLeaseId, SceneLease> drainProjectWorksites(FrontierWorldState state, SubjectId projectId) {
        Map<SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.replaceAll((id, lease) -> FrontierSceneBehaviors.isEngineeringWorksite(lease)
                && FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(projectId)
                && lease.status() == SceneLeaseStatus.HOT ? lease.withStatus(SceneLeaseStatus.DRAINING) : lease);
        return leases;
    }
}
