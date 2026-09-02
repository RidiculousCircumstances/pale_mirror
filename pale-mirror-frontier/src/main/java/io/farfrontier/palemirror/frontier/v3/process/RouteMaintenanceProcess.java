package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Admits and advances only in-place repair of one observed PM route loss.
 *
 * <p>This owner neither creates bypass waypoints nor writes Minecraft blocks. Source pickup and
 * target work remain separate physical intents owned by the same retained operation.</p>
 */
public final class RouteMaintenanceProcess {
    private static final SubjectId SYSTEM = new SubjectId("system:route-maintenance");
    private RouteMaintenanceProcess() { }

    public static ScheduledAction scan(int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:route-maintenance-" + ordinal), new SimInstant(dueAt), 0,
                SYSTEM, "frontier.route_maintenance.scan", 1);
    }

    public static ScheduledAction assemblyProgress(SubjectId maintenanceId, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:route-maintenance-assembly-" + maintenanceId.value().replace(':', '-')),
                new SimInstant(dueAt), 0, maintenanceId, "frontier.route_maintenance.assembly_progress", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value()) + 1;
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(ordinal, action.dueAt().ticks()
                + state.bootstrap().ruleset().cadence().routeConstructionScanInterval())));
        Optional<RouteMaintenance> active = state.routeMaintenances().values().stream().filter(RouteMaintenance::building)
                .sorted(Comparator.comparing(RouteMaintenance::id)).findFirst();
        Optional<RouteMaintenance> ready = state.routeMaintenances().values().stream().filter(value -> value.status() == RouteMaintenanceStatus.READY)
                .sorted(Comparator.comparing(RouteMaintenance::id)).findFirst();
        if (ready.isPresent()) {
            RouteMaintenance completed = ready.orElseThrow();
            if (!EngineeringEquipmentProcess.returnedOrLost(state, completed)) {
                return EngineeringEquipmentProcess.returnOne(state, completed).map(intent -> List.of(new ProposedEvent(completed.settlementId(),
                        new PhysicalIntentPrepared(intent)), next)).orElse(List.of(next));
            }
            boolean retainedScene = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isEngineeringWorksite)
                    .anyMatch(lease -> FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(completed.id())
                            && lease.status() != SceneLeaseStatus.CLOSED);
            return retainedScene ? List.of(next) : List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteMaintenanceClosed(completed.id())), next);
        }
        if (active.isEmpty()) return candidate(state).map(value -> List.of(new ProposedEvent(FrontierRouteNetwork.OWNER,
                new RouteMaintenanceStarted(value)), next)).orElse(List.of(next));
        RouteMaintenance maintenance = active.orElseThrow();
        if (state.physicalIntents().values().stream().anyMatch(intent -> (intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE
                || intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING)
                && intent.subjectIds().contains(maintenance.id())
                && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING))) return List.of(next);
        if (!EngineeringToolCustody.ready(state, maintenance.team())) {
            return EngineeringEquipmentProcess.issueOne(state, maintenance).map(intent -> List.of(new ProposedEvent(maintenance.settlementId(),
                    new PhysicalIntentPrepared(intent)), next)).orElse(List.of(next));
        }
        if (maintenance.assembly().isEmpty()) return admitAssembly(state, maintenance, action, next);
        if (!maintenance.assembly().orElseThrow().complete()) return List.of(next);
        if (maintenance.cargoId().isEmpty()) return material(state, maintenance).map(item -> List.of(new ProposedEvent(FrontierRouteNetwork.OWNER,
                new PhysicalIntentPrepared(materialLoadingIntent(state, maintenance, item)) ), next)).orElse(List.of(next));
        return List.of(next);
    }

    public static List<ProposedEvent> planAssemblyProgress(FrontierWorldState state, ScheduledAction action) {
        RouteMaintenance maintenance = state.routeMaintenances().get(action.subject());
        if (maintenance == null || !assemblyProgress(maintenance.id(), action.dueAt().ticks()).id().equals(action.id())
                || !maintenance.building() || maintenance.assembly().isEmpty()) return List.of();
        EngineeringWorkAssembly assembly = maintenance.assembly().orElseThrow();
        if (assembly.complete()) return List.of();
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(assemblyProgress(maintenance.id(), action.dueAt().ticks()
                + state.bootstrap().ruleset().cadence().migrationStepInterval())));
        boolean retainedHotOrRecovery = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isEngineeringWorksite)
                .anyMatch(lease -> FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(maintenance.id())
                        && lease.status() != SceneLeaseStatus.CLOSED);
        if (retainedHotOrRecovery) return List.of(next);
        SubjectId advancing = assembly.safeAdvances().stream().filter(member -> {
            AmbientActorLease lease = state.ambientLeases().get(member);
            return lease == null || lease.status() == AmbientLeaseStatus.CLOSED;
        }).findFirst().orElse(null);
        return advancing == null ? List.of(next) : List.of(new ProposedEvent(FrontierRouteNetwork.OWNER,
                new RouteMaintenanceAssemblyAdvanced(maintenance.id(), assembly.advance(advancing))), next);
    }

    private static List<ProposedEvent> admitAssembly(FrontierWorldState state, RouteMaintenance maintenance, ScheduledAction action, ProposedEvent next) {
        if (maintenance.team().memberIds().stream().map(state.ambientLeases()::get)
                .anyMatch(lease -> lease != null && lease.status() != AmbientLeaseStatus.CLOSED)) return List.of(next);
        EngineeringWorkAssembly assembly;
        try { assembly = EngineeringWorksite.compile(state, maintenance); }
        catch (IllegalArgumentException unavailable) { return List.of(next); }
        return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteMaintenanceAssemblyStarted(maintenance.id(), assembly)),
                new ProposedEvent(SYSTEM, new ScheduleEffect.Created(assemblyProgress(maintenance.id(), action.dueAt().ticks()
                        + state.bootstrap().ruleset().cadence().migrationStepInterval()))), next);
    }

    private static Optional<RouteMaintenance> candidate(FrontierWorldState state) {
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        return state.physicalDeltas().values().stream().filter(loss -> loss.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS)
                .filter(loss -> loss.ownerId().equals(Optional.of(FrontierRouteNetwork.OWNER)))
                .filter(loss -> loss.semanticPart().filter(RouteMaintenanceProcess::repairable).isPresent())
                .sorted(Comparator.comparingInt((PhysicalDelta loss) -> loss.position().x()).thenComparingInt(loss -> loss.position().y())
                        .thenComparingInt(loss -> loss.position().z())).filter(loss -> state.routeMaintenances().values().stream()
                        .noneMatch(existing -> existing.repairCell().equals(loss.position()))).map(loss -> admitted(state, loss, assignments)).flatMap(Optional::stream).findFirst();
    }

    /**
     * A PM-owned baseline loss has one recovery authority: in-place maintenance.
     *
     * <p>The physical observation and the strategic patrol are deliberately asynchronous.  A
     * bypass start must therefore reserve neither a crew nor a new topology merely because it
     * happened to be scheduled before the maintenance scan admitted its aggregate.  An admitted
     * maintenance is also retained as a guard after the loss has been observed.</p>
     */
    static boolean blocksBypassConstruction(FrontierWorldState state, SubjectId settlementId) {
        boolean retainedBaselineLoss = state.physicalDeltas().values().stream()
                .filter(loss -> loss.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS)
                .filter(loss -> loss.ownerId().equals(Optional.of(FrontierRouteNetwork.OWNER)))
                .filter(loss -> loss.semanticPart().filter(RouteMaintenanceProcess::repairable).isPresent())
                .anyMatch(loss -> FrontierRouteNetwork.containsOperationSurfaceCell(
                        state.routeTopology().supplyWaypoints(state.bootstrap(), settlementId), loss.position()));
        return retainedBaselineLoss || state.routeMaintenances().values().stream()
                .anyMatch(maintenance -> maintenance.settlementId().equals(settlementId)
                        && maintenance.status() != RouteMaintenanceStatus.CONFLICT);
    }

    private static Optional<RouteMaintenance> admitted(FrontierWorldState state, PhysicalDelta loss, HumanAssignmentProjection assignments) {
        SubjectId settlementId = state.bootstrap().settlements().stream().map(Settlement::id).sorted().filter(settlement ->
                FrontierRouteNetwork.containsOperationSurfaceCell(state.routeTopology().supplyWaypoints(state.bootstrap(), settlement), loss.position())).findFirst().orElse(null);
        if (settlementId == null) return Optional.empty();
        SubjectId id = new SubjectId("maintenance:route-" + loss.position().x() + "-" + loss.position().y() + "-" + loss.position().z());
        if (state.routeMaintenances().containsKey(id)) return Optional.empty();
        List<SubjectId> members = state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> assignments.idle(resident.id())).filter(resident -> FrontierWorldStateSupport.workCapable(state, resident))
                .filter(resident -> resident.capability(HumanCapability.ENGINEERING) > 0).sorted(Comparator.comparing(ResidentProfile::id))
                .limit(EngineeringRecoveryTeam.MIN_MEMBERS).map(ResidentProfile::id).toList();
        if (members.isEmpty()) return Optional.empty();
        return Optional.of(new RouteMaintenance(id, settlementId, loss.position(), loss.semanticPart().orElseThrow(), RouteMaintenanceStatus.BUILDING,
                Optional.empty(), EngineeringRecoveryTeam.forWorkOrder(id, settlementId, members), Optional.empty()));
    }

    private static boolean repairable(GrayboxSemanticPart part) {
        return part == GrayboxSemanticPart.ROUTE_SURFACE || part == GrayboxSemanticPart.ROUTE_FOUNDATION;
    }

    public static PhysicalIntent materialLoadingIntent(FrontierWorldState state, RouteMaintenance maintenance, ExactItemStack source) {
        if (!(source.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER)) {
            throw new IllegalArgumentException("route maintenance source must be in the exact maintenance container");
        }
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) throw new IllegalArgumentException("route maintenance source surface is not active");
        return new PhysicalIntent(new PhysicalIntentId("intent:route-maintenance-load-" + maintenance.id().value().replace(':', '-')),
                PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING, PhysicalIntentStatus.PREPARED, maintenance.id(),
                List.of(FrontierRouteNetwork.OWNER, maintenance.id(), maintenance.plannedCargoId(), maintenance.plannedCargoItemId(), source.id()),
                new FixedPosition(FixedScalar.whole(surface.position().x()), FixedScalar.whole(surface.position().y()), FixedScalar.whole(surface.position().z())), 0,
                PhysicalPostcondition.ROUTE_MAINTENANCE_MATERIAL_LOADED_OBSERVED);
    }

    public static PhysicalIntent workIntent(RouteMaintenance maintenance, SubjectId cargoId, SubjectId itemId) {
        BlockPosition position = maintenance.repairCell();
        return new PhysicalIntent(new PhysicalIntentId("intent:route-maintenance-" + maintenance.id().value().replace(':', '-')),
                PhysicalIntentKind.ROUTE_MAINTENANCE, PhysicalIntentStatus.PREPARED, FrontierRouteNetwork.OWNER,
                List.of(FrontierRouteNetwork.OWNER, maintenance.id(), cargoId, itemId),
                new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z())), 0,
                PhysicalPostcondition.ROUTE_MAINTENANCE_OBSERVED);
    }

    private static Optional<ExactItemStack> material(FrontierWorldState state, RouteMaintenance maintenance) {
        return state.inventory().items().values().stream().filter(item -> item.itemKind().equals(maintenance.expectedMaterial().repairItemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER))
                .filter(item -> state.inventory().surfaces().get(FrontierRouteNetwork.MAINTENANCE_CONTAINER) != null
                        && state.inventory().surfaces().get(FrontierRouteNetwork.MAINTENANCE_CONTAINER).status() == ContainerSurfaceStatus.ACTIVE)
                .sorted(Comparator.comparing(ExactItemStack::id)).findFirst();
    }

    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        RouteMaintenanceStateSupport.validateWorkIntent(state, intent);
        return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, transition));
    }

    public static List<ProposedEvent> planMaterialLoadingTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        RouteMaintenanceStateSupport.validateMaterialLoadingIntent(state, intent);
        if (transition.status() != PhysicalIntentStatus.CONFIRMED) return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, transition));
        if (!(transition.observation().orElseThrow() instanceof RouteMaintenanceMaterialLoadObservation observation)) {
            throw new IllegalArgumentException("route maintenance loading requires its exact observation");
        }
        RouteMaintenanceStateSupport.validateMaterialLoadingReceipt(state, intent, observation);
        return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, transition), new ProposedEvent(FrontierRouteNetwork.OWNER,
                new RouteMaintenanceMaterialLoaded(observation.maintenanceId(), new CargoBatch(observation.cargoId(), FrontierRouteNetwork.OWNER,
                        List.of(observation.cargoItemId())))));
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route maintenance intent must be prepared by the route network");
        if (intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE) RouteMaintenanceStateSupport.validateWorkIntent(state, intent);
        else if (intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING) RouteMaintenanceStateSupport.validateMaterialLoadingIntent(state, intent);
        else throw new IllegalArgumentException("route maintenance process received foreign intent");
        return state.preparePhysicalIntent(intent);
    }
}
