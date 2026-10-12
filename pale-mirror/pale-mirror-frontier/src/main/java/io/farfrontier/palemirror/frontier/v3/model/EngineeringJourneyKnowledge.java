package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy;
import java.util.HashSet;
import java.util.List;

/** Engineering declares its depot passage and crew reservations; shared knowledge owns geometry. */
public final class EngineeringJourneyKnowledge {
    private EngineeringJourneyKnowledge() { }
    static KnownPedestrianRouteKnowledge view(FrontierWorldState state, EngineeringWorkOrder owner) {
        var visit = owner.assembly().flatMap(EngineeringWorkAssembly::equipmentVisit).orElse(null);
        if (visit != null) return EngineeringDepotService.knowledge(state, visit.access());
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), owner.settlementId());
        SettlementStructure depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .reduce((left, right) -> { throw new IllegalArgumentException("engineering has duplicate depot facilities"); })
                .orElseThrow(() -> new IllegalArgumentException("engineering journey has no declared depot"));
        return KnownPedestrianRouteKnowledge.forSettlement(state, owner.settlementId(),
                List.of(new KnownPedestrianRouteKnowledge.Passage(depot, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }
    public static java.util.Optional<EngineeringApproachSelection> selectApproach(FrontierWorldState state, EngineeringWorkOrder owner) {
        var assembly = owner.assembly().orElseThrow();
        var executions = EngineeringExecutionAuthority.assemblyCurrent(state, owner);
        for (var actor : assembly.members().keySet().stream().sorted().toList()) {
            var member = assembly.members().get(actor);
            if (member.spatial().waitingOrigin().isEmpty() || !maySelect(state, actor)) continue;
            try {
                var selected = StationApproachNavigation.plan(approachView(state, owner, actor), member.spatial(),
                        order(owner, actor, member, executions.requireMember(actor).generation()));
                if (selected.isPresent()) return java.util.Optional.of(new EngineeringApproachSelection(actor, member.spatial(), selected.orElseThrow()));
            } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                // The shared calculation retains its typed result/dependency. Neither a
                // pending calculation nor NO_PATH authorizes a fabricated assembly step;
                // other independently movable crew members may still progress.
            }
        }
        return java.util.Optional.empty();
    }
    public static EngineeringWorkAssembly requireSelected(FrontierWorldState state, EngineeringWorkOrder owner,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions, EngineeringApproachSelection selected) {
        if (owner == null || owner.assembly().isEmpty() || (!owner.building() && !owner.readyForToolReturn()))
            throw new IllegalArgumentException("engineering approach lost its exact active owner");
        executions.requireDeclaration(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.ENGINEERING_ASSEMBLY,
                owner.id(), owner.engineeringTeam().orElseThrow().memberIds());
        executions.requireCurrent(state.actorExecutions());
        var member = owner.assembly().orElseThrow().members().get(selected.actorId());
        var actor = state.actorLocations().get(selected.actorId());
        if (member == null || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !member.spatial().equals(selected.predecessor()) || !maySelect(state, selected.actorId()))
            throw new IllegalArgumentException("engineering approach has a stale version or foreign member");
        StationApproachNavigation.requireSelected(approachView(state, owner, selected.actorId()), member.spatial(),
                order(owner, selected.actorId(), member, executions.requireMember(selected.actorId()).generation()), selected.approach());
        return owner.assembly().orElseThrow().withApproach(selected.actorId(), selected.approach());
    }
    private static KnownPedestrianRouteKnowledge approachView(FrontierWorldState state, EngineeringWorkOrder owner, SubjectId actor) {
        var reserved = new HashSet<SurfaceAnchor>();
        owner.assembly().orElseThrow().members().forEach((id, other) -> { if (!id.equals(actor)) reserved.add(other.currentSurface()); });
        return view(state, owner).withTaskExclusions(reserved);
    }
    private static boolean maySelect(FrontierWorldState state, SubjectId actor) {
        var lease = state.ambientLeases().get(actor);
        return lease == null || lease.status() == AmbientLeaseStatus.CLOSED || lease.status() == AmbientLeaseStatus.HOT;
    }
    private static MovementOrder order(EngineeringWorkOrder owner, SubjectId actor, EngineeringWorkAssembly.Member member, long generation) {
        var target = new SurfaceAnchor(member.corridor().get(Math.min(member.cursor() + 1, member.corridor().size() - 1)));
        return new MovementOrder(owner.id(), actor, generation, member.routeRevision(), List.of(target),
                TraversalCapability.PEDESTRIAN, ArrivalPolicy.EXACT_STATION);
    }
    public static FrontierWorldStateUpdate approachUpdate(FrontierWorldState state, SubjectId actor, EngineeringWorkAssembly assembly) {
        var update = FrontierWorldStateUpdate.begin();
        var lease = state.ambientLeases().get(actor);
        if (lease != null && lease.status() == AmbientLeaseStatus.HOT
                && lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) {
            var leases = new java.util.LinkedHashMap<>(state.ambientLeases());
            leases.put(actor, lease.withGoal(AmbientGoalKind.ENGINEERING_ASSEMBLY, assembly.members().get(actor).ambientTarget().standingBody()));
            update.ambientLeases(leases);
        }
        return update;
    }
    public static FrontierWorldStateUpdate successorUpdate(FrontierWorldState state, EngineeringWorkAssembly assembly) {
        var leases = new java.util.LinkedHashMap<>(state.ambientLeases());
        assembly.members().forEach((actor, member) -> {
            var lease = leases.get(actor);
            if (lease != null && lease.status() == AmbientLeaseStatus.HOT)
                leases.put(actor, lease.withGoal(AmbientGoalKind.ENGINEERING_ASSEMBLY, member.ambientTarget().standingBody()));
        });
        return FrontierWorldStateUpdate.begin().ambientLeases(leases);
    }
    public static boolean openEdge(FrontierWorldState state, EngineeringWorkOrder owner, SubjectId actor) {
        var member = owner.assembly().orElseThrow().members().get(actor);
        return member.canAdvance() && EngineeringEquipmentServiceAccess.mayAdvance(state, owner, actor, member.nextSurface())
                && view(state, owner).traversable(List.of(member.currentSurface(), member.nextSurface()));
    }
}
