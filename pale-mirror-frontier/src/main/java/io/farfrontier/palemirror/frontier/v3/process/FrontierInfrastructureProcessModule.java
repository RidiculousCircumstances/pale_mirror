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
            default -> throw new IllegalArgumentException("infrastructure process does not own event: " + event.payload().type());
        };
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
