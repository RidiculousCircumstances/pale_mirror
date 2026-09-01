package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;

import java.util.Comparator;
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
    public static Optional<EngineeringWorkSceneCandidate> candidate(FrontierWorldState state, RouteConstruction project) {
        if (project.status() != RouteConstructionStatus.BUILDING || project.team().isEmpty() || project.assembly().isEmpty()) return Optional.empty();
        EngineeringRecoveryTeam team = project.team().orElseThrow();
        EngineeringWorkAssembly assembly = project.assembly().orElseThrow();
        if (!assembly.complete() || !EngineeringToolCustody.ready(state, team)
                || project.confirmedCells() >= project.workCells().size()) return Optional.empty();
        Map<SubjectId, BlockPosition> positions = assembly.positions();
        if (!positions.keySet().equals(Set.copyOf(team.memberIds())) || positions.entrySet().stream()
                .anyMatch(entry -> !state.actorLocations().get(entry.getKey()).position().equals(entry.getValue()))) return Optional.empty();
        return Optional.of(new EngineeringWorkSceneCandidate(project.id(), project.confirmedCells(),
                EngineeringWorksite.workCell(state.bootstrap(), state.routeTopology(), project), positions));
    }

    public static Optional<EngineeringWorkSceneCandidate> nextCandidate(FrontierWorldState state) {
        return state.routeConstructions().values().stream().sorted(Comparator.comparing(RouteConstruction::id))
                .map(project -> candidate(state, project)).flatMap(Optional::stream).findFirst();
    }

    public static RouteConstruction require(FrontierWorldState state, EngineeringWorkSceneCause cause) {
        RouteConstruction project = state.routeConstructions().get(cause.projectId());
        if (project == null) throw new IllegalArgumentException("engineering scene has no active construction project");
        if (project.confirmedCells() != cause.workCellIndex()) throw new IllegalArgumentException("engineering scene cursor differs from current construction cell");
        return project;
    }

    public static SubjectId owner(FrontierWorldState state, EngineeringWorkSceneCause cause) {
        // A confirmed physical cell advances the construction cursor before its old HOT
        // work-site lease drains.  Owner resolution is intentionally broader than admission:
        // the old lease must still close or recover under the same project/settlement after
        // that advance, rather than being misread as a logistics scene or becoming ownerless.
        RouteConstruction project = state.routeConstructions().get(cause.projectId());
        if (project == null) throw new IllegalArgumentException("engineering scene has no active construction project");
        return project.settlementId();
    }

    public static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        EngineeringWorkSceneCause cause = FrontierSceneBehaviors.engineeringWorksite(lease);
        RouteConstruction project = require(state, cause);
        EngineeringWorkSceneCandidate candidate = candidate(state, project)
                .orElseThrow(() -> new IllegalArgumentException("engineering scene has no exact COLD-ready crew"));
        if (!lease.handoffPosition().equals(candidate.workCell()) || !lease.memberPositions().equals(candidate.memberPositions())
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
        RouteConstruction project = intent.subjectIds().stream().map(state.routeConstructions()::get)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (project == null || project.team().isEmpty() || project.cargoId().isEmpty()) return false;
        return state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isEngineeringWorksite)
                .filter(lease -> lease.status() == SceneLeaseStatus.HOT).anyMatch(lease -> {
                    EngineeringWorkSceneCause cause = FrontierSceneBehaviors.engineeringWorksite(lease);
                    return cause.projectId().equals(project.id()) && cause.workCellIndex() == project.confirmedCells()
                            && lease.handoffPosition().equals(EngineeringWorksite.workCell(state.bootstrap(), state.routeTopology(), project));
                });
    }
}
