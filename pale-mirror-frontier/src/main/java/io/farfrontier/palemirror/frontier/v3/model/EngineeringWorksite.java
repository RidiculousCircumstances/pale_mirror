package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stable work-site slots for the next exact route-construction cell. */
public final class EngineeringWorksite {
    private static final List<Offset> SLOT_OFFSETS = List.of(new Offset(-1, -1), new Offset(-1, 1), new Offset(1, -1), new Offset(1, 1));

    private EngineeringWorksite() { }

    public static EngineeringWorkAssembly compile(FrontierWorldState state, RouteConstruction project) {
        Objects.requireNonNull(state, "engineering work-site state");
        EngineeringRecoveryTeam team = requireTeam(project);
        List<BlockPosition> slots = slots(state.bootstrap(), state.routeTopology(), project);
        Map<SubjectId, EngineeringWorkAssembly.Member> members = new LinkedHashMap<>();
        for (int index = 0; index < team.memberIds().size(); index++) {
            SubjectId member = team.memberIds().get(index);
            try {
                members.put(member, new EngineeringWorkAssembly.Member(EngineeringApproachCorridor.compile(state, member, slots.get(index)), 0));
            } catch (IllegalArgumentException unavailable) {
                throw new IllegalArgumentException("engineering work-site has no bounded approach for " + member.value() + " from "
                        + state.actorLocations().get(member).position() + " to " + slots.get(index) + ": " + unavailable.getMessage(), unavailable);
            }
        }
        return new EngineeringWorkAssembly(members);
    }

    static void validate(FrontierBootstrap bootstrap, RouteTopology topology, RouteConstruction project) {
        if (project.assembly().isEmpty()) return;
        EngineeringRecoveryTeam team = requireTeam(project);
        EngineeringWorkAssembly assembly = project.assembly().orElseThrow();
        if (!assembly.members().keySet().equals(java.util.Set.copyOf(team.memberIds()))) {
            throw new IllegalArgumentException("engineering assembly changes its retained crew");
        }
        List<BlockPosition> slots = slots(bootstrap, topology, project);
        if (!assembly.members().values().stream().map(EngineeringWorkAssembly.Member::destination).collect(java.util.stream.Collectors.toSet())
                .equals(java.util.Set.copyOf(slots.subList(0, team.memberIds().size())))) {
            throw new IllegalArgumentException("engineering assembly changes its declared work-site positions");
        }
    }

    public static BlockPosition workCell(FrontierBootstrap bootstrap, RouteTopology topology, RouteConstruction project) {
        List<BlockPosition> cells = project.workCells().isEmpty()
                ? FrontierRouteNetwork.constructionCells(bootstrap, topology, project.settlementId(), project.waypoints()) : project.workCells();
        if (project.confirmedCells() >= cells.size()) throw new IllegalArgumentException("ready construction has no remaining work cell");
        return cells.get(project.confirmedCells());
    }

    private static List<BlockPosition> slots(FrontierBootstrap bootstrap, RouteTopology topology, RouteConstruction project) {
        // Canonical actor positions are column anchors; the NeoForge materializer resolves
        // their actual standing cell through FrontierV3StandingPosition. Keep the engineering
        // corridor on the same anchor plane as the route floor rather than embedding adapter Y.
        BlockPosition anchorPlane = workCell(bootstrap, topology, project);
        return SLOT_OFFSETS.stream().map(offset -> anchorPlane.offset(offset.x(), 0, offset.z())).toList();
    }

    private static EngineeringRecoveryTeam requireTeam(RouteConstruction project) {
        Objects.requireNonNull(project, "engineering work-site project");
        if (project.status() != RouteConstructionStatus.BUILDING || project.team().isEmpty()) {
            throw new IllegalArgumentException("engineering work-site needs one active exact construction crew");
        }
        return project.team().orElseThrow();
    }

    private record Offset(int x, int z) { }
}
