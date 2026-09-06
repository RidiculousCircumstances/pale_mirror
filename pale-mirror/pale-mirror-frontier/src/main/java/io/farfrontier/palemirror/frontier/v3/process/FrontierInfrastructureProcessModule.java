package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.List;

/** Exact reducer owner for route construction and patrol facts. */
final class FrontierInfrastructureProcessModule implements FrontierWorldProcessModule {
    @Override public CommandPlan planCommand(FrontierWorldState state, FrontierCommand command) {
        if (command.payload() instanceof RoutePatrolSceneLeasePrepared prepared) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierRoutePatrolSceneSupport.owner(state,
                    FrontierSceneBehaviors.routePatrol(prepared.lease())), prepared))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof RoutePatrolSceneLeaseHandoff handoff) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierRoutePatrolSceneSupport.owner(state,
                    FrontierSceneBehaviors.routePatrol(handoff.lease())), handoff))); }
            catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof RoutePatrolTraversalObserved observed) return planPatrolTraversal(state, observed);
        if (command.payload() instanceof RoutePatrolBlocked blocked) return planPatrolBlocked(state, blocked);
        if (command.payload() instanceof RoutePatrolObstructionConfirmed confirmed) return planPatrolObstruction(state, confirmed, command.submittedAt().ticks());
        if (command.payload() instanceof RouteConstructionAssemblyAdvanced advanced) {
            RouteConstruction project = state.routeConstructions().get(advanced.projectId());
            return planEngineeringAssemblyAdvance(state, command, project, advanced.assembly(), advanced,
                    "route construction assembly observation has no active project");
        }
        if (command.payload() instanceof RouteMaintenanceAssemblyAdvanced advanced) {
            RouteMaintenance maintenance = state.routeMaintenances().get(advanced.maintenanceId());
            return planEngineeringAssemblyAdvance(state, command, maintenance, advanced.assembly(), advanced,
                    "route maintenance assembly observation has no active operation");
        }
        return FrontierWorldCommandPlanner.rejected("infrastructure process does not admit command: " + command.payload().type());
    }

    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        return switch (event.payload()) {
            case RouteConstructionStarted started -> RouteConstructionStateSupport.reduceStarted(state, event.subject(), started);
            case RouteConstructionMaterialLoaded loaded -> RouteConstructionStateSupport.reduceMaterialLoaded(state, event.subject(), loaded);
            case RouteConstructionAssemblyStarted started -> RouteConstructionStateSupport.reduceAssemblyStarted(state, event.subject(), started);
            case RouteConstructionAssemblyAdvanced advanced -> RouteConstructionStateSupport.reduceAssemblyAdvanced(state, event.subject(), advanced);
            case RouteTopologyCutover cutover -> RouteConstructionStateSupport.reduceCutover(state, event.subject(), cutover);
            case RouteMaintenanceStarted started -> RouteMaintenanceStateSupport.reduceStarted(state, event.subject(), started);
            case RouteMaintenanceMaterialLoaded loaded -> RouteMaintenanceStateSupport.reduceMaterialLoaded(state, event.subject(), loaded);
            case RouteMaintenanceAssemblyStarted started -> RouteMaintenanceStateSupport.reduceAssemblyStarted(state, event.subject(), started);
            case RouteMaintenanceAssemblyAdvanced advanced -> RouteMaintenanceStateSupport.reduceAssemblyAdvanced(state, event.subject(), advanced);
            case RouteMaintenanceClosed closed -> RouteMaintenanceStateSupport.reduceClosed(state, event.subject(), closed);
            case RoutePatrolStarted started -> RoutePatrolProcess.reduceStarted(state, event.subject(), started);
            case RoutePatrolAdvanced advanced -> RoutePatrolProcess.reduceAdvanced(state, event.subject(), advanced);
            case RoutePatrolObstructionConfirmed confirmed -> RoutePatrolProcess.reduceObstruction(state, event.subject(), confirmed);
            case RoutePatrolFailed failed -> RoutePatrolProcess.reduceFailed(state, event.subject(), failed);
            case RoutePatrolBlocked blocked -> RoutePatrolProcess.reduceBlocked(state, event.subject(), blocked);
            case RoutePatrolSceneLeasePrepared prepared -> reducePatrolPrepared(state, event.subject(), event, prepared);
            case RoutePatrolSceneLeaseHandoff handoff -> reducePatrolHandoff(state, event.subject(), event, handoff);
            case RoutePatrolTraversalObserved observed -> reducePatrolTraversal(state, event.subject(), observed);
            default -> throw new IllegalArgumentException("infrastructure process does not own event: " + event.payload().type());
        };
    }

    private static CommandPlan planPatrolTraversal(FrontierWorldState state, RoutePatrolTraversalObserved observed) {
        try {
            RoutePatrol patrol = FrontierRoutePatrolSceneSupport.require(state, new RoutePatrolSceneCause(observed.taskId()));
            FrontierRoutePatrolSceneSupport.advanceObserved(state, patrol, observed.leaseId(), observed.actorId(), observed.observedBody());
            RoutePatrol next = patrol.advance(observed.actorId());
            List<ProposedEvent> events = new java.util.ArrayList<>(List.of(new ProposedEvent(patrol.settlementId(), observed)));
            if (next.status() == RoutePatrolStatus.ROUTE_CLEAR) {
                events.add(new ProposedEvent(patrol.settlementId(), new StrategicTaskTransition(patrol.taskId(), StrategicTaskStatus.COMPLETED)));
            }
            return new CommandPlan.Accepted(List.copyOf(events));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static CommandPlan planPatrolBlocked(FrontierWorldState state, RoutePatrolBlocked blocked) {
        try {
            RoutePatrol patrol = state.strategicPlans().routePatrols().get(blocked.taskId());
            if (patrol == null || !patrol.active()) throw new IllegalArgumentException("route-patrol block has no active patrol");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(patrol.settlementId(), blocked),
                    new ProposedEvent(patrol.settlementId(), new StrategicTaskTransition(patrol.taskId(), StrategicTaskStatus.BLOCKED))));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static CommandPlan planPatrolObstruction(FrontierWorldState state, RoutePatrolObstructionConfirmed confirmed, long submittedAt) {
        try {
            RoutePatrol patrol = state.strategicPlans().routePatrols().get(confirmed.taskId());
            if (patrol == null || !patrol.active() || !state.physicalDeltas().containsKey(confirmed.position())) {
                throw new IllegalArgumentException("route-patrol obstruction has no retained physical evidence");
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(patrol.settlementId(), confirmed),
                    new ProposedEvent(patrol.settlementId(), new StrategicTaskTransition(patrol.taskId(), StrategicTaskStatus.COMPLETED)),
                    new ProposedEvent(patrol.settlementId(), new ScheduleEffect.Created(StrategicObjectiveProcess.routeReconsideration(patrol.settlementId(),
                            confirmed.position(), "confirmed", Math.addExact(submittedAt, 1L))))));
        } catch (IllegalArgumentException invalid) { return FrontierWorldCommandPlanner.rejected(invalid.getMessage()); }
    }

    private static FrontierWorldState reducePatrolPrepared(FrontierWorldState state, SubjectId subject, FrontierEvent event,
                                                           RoutePatrolSceneLeasePrepared prepared) {
        if (!subject.equals(FrontierRoutePatrolSceneSupport.owner(state, FrontierSceneBehaviors.routePatrol(prepared.lease())))
                || !prepared.lease().handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("route-patrol scene preparation does not match its retained formation");
        }
        return state.prepareSceneLease(prepared.lease());
    }

    private static FrontierWorldState reducePatrolHandoff(FrontierWorldState state, SubjectId subject, FrontierEvent event,
                                                          RoutePatrolSceneLeaseHandoff handoff) {
        if (!subject.equals(FrontierRoutePatrolSceneSupport.owner(state, FrontierSceneBehaviors.routePatrol(handoff.lease())))
                || !handoff.lease().handoffInstant().equals(event.instant())) {
            throw new IllegalArgumentException("route-patrol scene hand-off does not match its retained formation");
        }
        return state.handoffAmbientScene(new SceneLeaseHandoff(handoff.lease(), handoff.ambientMembers()));
    }

    private static FrontierWorldState reducePatrolTraversal(FrontierWorldState state, SubjectId subject, RoutePatrolTraversalObserved observed) {
        RoutePatrol patrol = FrontierRoutePatrolSceneSupport.require(state, new RoutePatrolSceneCause(observed.taskId()));
        if (!subject.equals(patrol.settlementId())) throw new IllegalArgumentException("route-patrol traversal has a foreign owner");
        return FrontierRoutePatrolSceneSupport.advanceObserved(state, patrol, observed.leaseId(), observed.actorId(), observed.observedBody());
    }

    private static CommandPlan planEngineeringAssemblyAdvance(FrontierWorldState state, FrontierCommand command,
                                                               EngineeringWorkOrder project, EngineeringWorkAssembly next,
                                                               io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload,
                                                               String missingOwner) {
        if (project == null) return FrontierWorldCommandPlanner.rejected(missingOwner);
        try {
            SubjectId observed = validateHotEngineeringAssemblyObservation(state, project, next);
            List<ProposedEvent> events = next.complete()
                    ? List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, payload), new ProposedEvent(project.id(),
                    new ScheduleEffect.Created(progress(project, command.submittedAt().ticks() + 1L))))
                    : List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, payload));
            return new CommandPlan.Accepted(events);
        } catch (IllegalArgumentException invalid) {
            return FrontierWorldCommandPlanner.rejected(invalid.getMessage());
        }
    }

    /** The physical boundary may acknowledge exactly one live body at its existing lease goal. */
    private static SubjectId validateHotEngineeringAssemblyObservation(FrontierWorldState state, EngineeringWorkOrder project,
                                                                        EngineeringWorkAssembly next) {
        EngineeringWorkAssembly current = project.assembly().orElseThrow(() -> new IllegalArgumentException("engineering work has no active assembly"));
        if (!current.members().keySet().equals(next.members().keySet())) {
            throw new IllegalArgumentException("HOT engineering assembly observation changes the retained crew");
        }
        SubjectId observed = null;
        for (SubjectId actor : current.members().keySet()) {
            EngineeringWorkAssembly.Member before = current.members().get(actor);
            EngineeringWorkAssembly.Member after = next.members().get(actor);
            if (!before.corridor().equals(after.corridor()) || after.cursor() < before.cursor() || after.cursor() > before.cursor() + 1) {
                throw new IllegalArgumentException("HOT engineering assembly observation may advance only one adjacent cursor");
            }
            if (after.cursor() > before.cursor()) {
                if (observed != null) throw new IllegalArgumentException("HOT engineering assembly observation may acknowledge only one actor");
                observed = actor;
            }
        }
        if (observed == null || !current.advance(observed).equals(next)) {
            throw new IllegalArgumentException("HOT engineering assembly observation is not the retained safe next step");
        }
        EngineeringWorkAssembly.Member arrived = next.members().get(observed);
        AmbientActorLease lease = state.ambientLeases().get(observed);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY
                || !lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(arrived.currentPosition())))) {
            throw new IllegalArgumentException("HOT engineering assembly observation lacks its exact active actor lease");
        }
        return observed;
    }

    private static io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction progress(EngineeringWorkOrder project, long dueAt) {
        return switch (project) {
            case RouteConstruction construction -> RouteConstructionProcess.progress(construction, dueAt);
            case RouteMaintenance maintenance -> RouteMaintenanceProcess.progress(maintenance, dueAt);
        };
    }
}
