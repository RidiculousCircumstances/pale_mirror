package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stable work-site slots for the next exact route-construction cell. */
public final class EngineeringWorksite {
    private static final List<Offset> SLOT_OFFSETS = List.of(new Offset(-1, -1), new Offset(-1, 1), new Offset(1, -1), new Offset(1, 1));
    /**
     * A construction team contains at most four people and each member has a small fixed lane
     * catalogue.  This ceiling keeps joint compilation a bounded pure operation, rather than
     * turning COLD approach into an unbounded live pathfinder.
     */
    private static final int MAX_JOINT_CORRIDOR_COMBINATIONS = 256;

    private EngineeringWorksite() { }

    public static EngineeringWorkAssembly compile(FrontierWorldState state, RouteConstruction project) {
        Objects.requireNonNull(state, "engineering work-site state");
        EngineeringRecoveryTeam team = requireTeam(project);
        List<BlockPosition> slots = slots(state.bootstrap(), state.routeTopology(), project);
        Map<SubjectId, List<List<BlockPosition>>> candidates = new LinkedHashMap<>();
        for (int index = 0; index < team.memberIds().size(); index++) {
            SubjectId member = team.memberIds().get(index);
            BlockPosition start = Objects.requireNonNull(state.actorLocations().get(member), "engineering work-site member location").position();
            BlockPosition destination = slots.get(index);
            try {
                List<List<BlockPosition>> memberCandidates = EngineeringApproachCorridor.candidates(state, member, destination);
                if (memberCandidates.isEmpty()) throw new IllegalArgumentException("no clear bounded public lane");
                candidates.put(member, memberCandidates);
            } catch (IllegalArgumentException unavailable) {
                throw new IllegalArgumentException("engineering work-site has no bounded approach for " + member.value() + " from "
                        + start + " to " + destination + ": " + unavailable.getMessage(), unavailable);
            }
        }
        EngineeringWorkAssembly compiled = compileJoint(team.memberIds(), candidates);
        if (compiled == null) {
            throw new IllegalArgumentException("engineering work-site has no collision-free bounded joint approach");
        }
        return compiled;
    }

    /**
     * Selects immutable individual corridors only when their deterministic COLD scheduler can
     * take the whole retained team to its distinct slots.  Crossing lanes are permitted: the
     * scheduler serializes the exact people through them.  A static all-cells-disjoint rule
     * would reject ordinary settlement geometry, while independent first-lane selection can
     * deadlock on a head-on exchange.
     */
    private static EngineeringWorkAssembly compileJoint(List<SubjectId> members, Map<SubjectId, List<List<BlockPosition>>> candidates) {
        AttemptBudget budget = new AttemptBudget();
        int largestCatalogue = candidates.values().stream().mapToInt(List::size).max().orElseThrow();
        for (int ceiling = 0; ceiling < largestCatalogue && !budget.exhausted(); ceiling++) {
            EngineeringWorkAssembly compiled = compileJoint(members, candidates, 0, ceiling, false, new LinkedHashMap<>(), budget);
            if (compiled != null) return compiled;
        }
        return null;
    }

    /**
     * Enumerates lane combinations in widening priority rings. A naive lexicographic product
     * spends its complete bounded budget varying only the final person, even when a safe plan
     * requires each person to take their second local lane. A ring is stable and finite; at
     * least one member must use its ceiling lane so earlier rings are never retried.
     */
    private static EngineeringWorkAssembly compileJoint(List<SubjectId> members, Map<SubjectId, List<List<BlockPosition>>> candidates,
                                                          int memberIndex, int ceiling, boolean usesCeiling,
                                                          Map<SubjectId, EngineeringWorkAssembly.Member> selected, AttemptBudget budget) {
        if (budget.exhausted()) return null;
        if (memberIndex == members.size()) {
            if (!usesCeiling) return null;
            budget.recordAttempt();
            EngineeringWorkAssembly assembly;
            try {
                assembly = new EngineeringWorkAssembly(selected);
            } catch (IllegalArgumentException invalid) {
                return null;
            }
            return completesUnderRetainedSchedule(assembly) ? assembly : null;
        }
        SubjectId member = members.get(memberIndex);
        List<List<BlockPosition>> options = candidates.get(member);
        for (int option = 0; option <= Math.min(ceiling, options.size() - 1); option++) {
            List<BlockPosition> corridor = options.get(option);
            selected.put(member, new EngineeringWorkAssembly.Member(corridor, 0));
            EngineeringWorkAssembly compiled = compileJoint(members, candidates, memberIndex + 1, ceiling, usesCeiling || option == ceiling,
                    selected, budget);
            if (compiled != null) return compiled;
            if (budget.exhausted()) break;
        }
        selected.remove(member);
        return null;
    }

    private static boolean completesUnderRetainedSchedule(EngineeringWorkAssembly initial) {
        EngineeringWorkAssembly assembly = initial;
        int remainingMoves = assembly.members().values().stream().mapToInt(member -> member.corridor().size() - 1).sum();
        for (int move = 0; move < remainingMoves; move++) {
            SubjectId advancing = assembly.nextSafeAdvance().orElse(null);
            if (advancing == null) return false;
            assembly = assembly.advance(advancing);
        }
        return assembly.complete();
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

    private static final class AttemptBudget {
        private int attempts;
        boolean exhausted() { return attempts >= MAX_JOINT_CORRIDOR_COMBINATIONS; }
        void recordAttempt() { attempts++; }
    }

    private record Offset(int x, int z) { }
}
