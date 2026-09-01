package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
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
    /** Total retained scheduler transitions evaluated while compiling one work-site team. */
    private static final int MAX_JOINT_SCHEDULER_STEPS = 131_072;

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
            return completesUnderRetainedSchedule(assembly, budget) ? assembly : null;
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

    private static boolean completesUnderRetainedSchedule(EngineeringWorkAssembly initial, AttemptBudget budget) {
        // This is compilation proof, not canonical movement.  Reconstructing an immutable
        // EngineeringWorkAssembly for each hypothetical step used to allocate a full map and
        // corridor wrapper per transition, making a bounded candidate search capable of
        // stalling the server tick that admits a repair.  Keep exactly the same ordered
        // queue/swap rule as EngineeringWorkAssembly.nextSafeAdvance(), but model only the
        // four cursors locally.  The retained assembly is still the sole published result.
        List<SchedulerMember> members = initial.members().entrySet().stream()
                .map(entry -> new SchedulerMember(entry.getKey(), entry.getValue().corridor(), entry.getValue().cursor()))
                .sorted(Comparator.comparing(member -> member.actor().value()))
                .toList();
        int[] cursors = members.stream().mapToInt(SchedulerMember::cursor).toArray();
        int remainingMoves = 0;
        for (int index = 0; index < members.size(); index++) {
            remainingMoves += members.get(index).corridor().size() - cursors[index] - 1;
        }
        for (int move = 0; move < remainingMoves; move++) {
            if (!budget.recordSchedulerStep()) return false;
            int advancing = nextSafeAdvance(members, cursors);
            if (advancing < 0) return false;
            cursors[advancing]++;
        }
        for (int index = 0; index < members.size(); index++) {
            if (cursors[index] != members.get(index).corridor().size() - 1) return false;
        }
        return true;
    }

    /** Mirrors the published assembly's deterministic safe-move semantics without allocations. */
    private static int nextSafeAdvance(List<SchedulerMember> members, int[] cursors) {
        int advancing = -1;
        for (int index = 0; index < members.size(); index++) {
            int leader = advanceLeader(index, members, cursors, new boolean[members.size()]);
            if (leader >= 0 && (advancing < 0
                    || members.get(leader).actor().compareTo(members.get(advancing).actor()) < 0)) {
                advancing = leader;
            }
        }
        return advancing;
    }

    /** Resolves a queue from its empty leading cell backwards; cycles and arrived blockers wait. */
    private static int advanceLeader(int actorIndex, List<SchedulerMember> members, int[] cursors, boolean[] visiting) {
        SchedulerMember actor = members.get(actorIndex);
        if (cursors[actorIndex] >= actor.corridor().size() - 1 || visiting[actorIndex]) return -1;
        visiting[actorIndex] = true;
        BlockPosition next = actor.corridor().get(cursors[actorIndex] + 1);
        for (int index = 0; index < members.size(); index++) {
            if (index != actorIndex && members.get(index).corridor().get(cursors[index]).equals(next)) {
                return advanceLeader(index, members, cursors, visiting);
            }
        }
        return actorIndex;
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

    /**
     * Immutable support columns for the current construction front.  They have a separate
     * semantic owner from completed route cells: an engineer may stand on one, but it never
     * grants route passability or construction credit.
     *
     * <p>The baseline intentionally remains available after a project has failed, so an
     * already observed loss can survive validation and explain that failure.  A failed project
     * cannot advance its cursor, therefore this is still one exact bounded footprint.</p>
     */
    public static List<BlockPosition> intactStagingCells(FrontierBootstrap bootstrap, RouteTopology topology, RouteConstruction project) {
        Objects.requireNonNull(bootstrap, "engineering staging bootstrap"); Objects.requireNonNull(topology, "engineering staging topology");
        Objects.requireNonNull(project, "engineering staging project");
        if (project.team().isEmpty() || project.confirmedCells() >= project.workCells().size()) return List.of();
        return List.copyOf(slots(bootstrap, topology, project).subList(0, project.team().orElseThrow().memberIds().size()));
    }

    /** Current desired staging exists only for a supplied, assembled active work front. */
    public static List<BlockPosition> activeStagingCells(FrontierBootstrap bootstrap, RouteTopology topology, RouteConstruction project) {
        if (project.status() != RouteConstructionStatus.BUILDING || project.cargoId().isEmpty()
                || project.assembly().isEmpty() || !project.assembly().orElseThrow().complete()) return List.of();
        return intactStagingCells(bootstrap, topology, project);
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
        private int schedulerSteps;
        boolean exhausted() { return attempts >= MAX_JOINT_CORRIDOR_COMBINATIONS; }
        void recordAttempt() { attempts++; }
        boolean recordSchedulerStep() { return ++schedulerSteps <= MAX_JOINT_SCHEDULER_STEPS; }
    }

    private record SchedulerMember(SubjectId actor, List<BlockPosition> corridor, int cursor) { }

    private record Offset(int x, int z) { }
}
