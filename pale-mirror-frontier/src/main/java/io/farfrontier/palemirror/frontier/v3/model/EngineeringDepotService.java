package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Pure compiler and proof for exact engineering hand-offs at one depot service port. */
public final class EngineeringDepotService {
    private EngineeringDepotService() { }

    public static EngineeringWorkAssembly compile(FrontierWorldState state, EngineeringWorkOrder project,
                                                  EngineeringJourneyPurpose purpose) {
        Objects.requireNonNull(state, "engineering depot state"); Objects.requireNonNull(project, "engineering depot project");
        if (purpose != EngineeringJourneyPurpose.MUSTER_DEPOT && purpose != EngineeringJourneyPurpose.RETURN_DEPOT) {
            throw new IllegalArgumentException("depot service may compile only a muster or return journey");
        }
        SettlementDepotServicePort port = port(state, project);
        List<BlockPosition> stations = port.stations().stream().map(SurfaceAnchor::support).toList();
        return EngineeringWorksite.compileJourney(state, project, purpose, stations);
    }

    /** Canonical precondition for an exact local chest/hand issue or return. */
    public static boolean atStations(FrontierWorldState state, EngineeringWorkOrder project, EngineeringJourneyPurpose purpose) {
        Objects.requireNonNull(state, "engineering depot state"); Objects.requireNonNull(project, "engineering depot project");
        EngineeringWorkAssembly journey = project.assembly().orElse(null);
        if (journey == null || journey.purpose() != purpose || !journey.complete()) return false;
        SettlementDepotServicePort port = port(state, project);
        java.util.Set<BlockPosition> expected = java.util.Set.copyOf(port.stations().stream().map(SurfaceAnchor::support).toList());
        return journey.members().keySet().equals(java.util.Set.copyOf(project.engineeringTeam().orElseThrow().memberIds()))
                && journey.positions().values().stream().allMatch(expected::contains)
                && journey.positions().values().stream().distinct().count() == journey.positions().size()
                && journey.positions().entrySet().stream().allMatch(entry -> {
                    ActorLocation actor = state.actorLocations().get(entry.getKey());
                    return actor != null && actor.condition().status() == ActorLifeStatus.ALIVE
                            && actor.supportingSurface().support().equals(entry.getValue());
                });
    }

    public static SettlementDepotServicePort port(FrontierWorldState state, EngineeringWorkOrder project) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), project.settlementId());
        SettlementStructure depot = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("engineering settlement has no depot"));
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        ContainerSurface surface = state.inventory().surfaces().get(FrontierWorldState.depotId(project.settlementId()));
        if (surface == null || !surface.position().equals(port.containerPosition())) {
            throw new IllegalArgumentException("engineering depot service socket differs from its exact inventory surface");
        }
        return port;
    }

    static void validate(FrontierBootstrap bootstrap, EngineeringWorkOrder project, EngineeringWorkAssembly journey) {
        if (journey.purpose() == EngineeringJourneyPurpose.WORKSITE) {
            throw new IllegalArgumentException("worksite journey is not a depot service journey");
        }
        Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, project.settlementId());
        SettlementStructure depot = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("engineering settlement has no depot"));
        java.util.Set<BlockPosition> stations = java.util.Set.copyOf(SettlementDepotServicePort.forDepot(depot).stations().stream()
                .map(SurfaceAnchor::support).toList());
        if (!journey.members().values().stream().map(EngineeringWorkAssembly.Member::destination).allMatch(stations::contains)) {
            throw new IllegalArgumentException("engineering depot journey changes its declared service stations");
        }
    }
}
