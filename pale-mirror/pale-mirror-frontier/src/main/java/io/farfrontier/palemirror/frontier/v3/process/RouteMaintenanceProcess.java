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

    /**
     * Durable continuation of an admitted maintenance owner.  This is deliberately separate
     * from the slow strategic scan: an observed local journey arrival, tool hand-off or work
     * completion must make the same retained owner actionable on the normal human cadence.
     */
    public static ScheduledAction progress(RouteMaintenance maintenance, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:route-maintenance-progress-" + maintenance.id().value().replace(':', '-')
                + "-at-" + dueAt), new SimInstant(dueAt), 0, maintenance.id(), "frontier.route_maintenance.progress", 1);
    }

    /** Durable reactive continuation after a physical repair has made the exact crew returnable. */
    public static ScheduledAction returnProgress(RouteMaintenance maintenance, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:route-maintenance-return-" + maintenance.id().value().replace(':', '-')
                + "-at-" + dueAt), new SimInstant(dueAt), 0, maintenance.id(), "frontier.route_maintenance.return_progress", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        int scanOrdinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        int nextOrdinal = scanOrdinal + 1;
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(nextOrdinal, action.dueAt().ticks()
                + state.bootstrap().ruleset().cadence().routeConstructionScanInterval())));
        java.util.ArrayList<ProposedEvent> events = new java.util.ArrayList<>();
        nextMaintainedOwner(state, scanOrdinal).ifPresent(maintenance -> events.addAll(maintenance.status() == RouteMaintenanceStatus.READY
                ? planReady(state, maintenance, action, Optional.empty()) : planBuilding(state, maintenance, action, Optional.empty())));
        candidate(state).ifPresent(value -> events.add(new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteMaintenanceStarted(value))));
        events.add(next);
        return List.copyOf(events);
    }

    /**
     * One scan advances one retained owner in deterministic rotation.  This is deliberately not
     * a global first-ID queue: a COLD depot or an under-supplied crew must not suppress another
     * exact repair cell whose people and physical endpoint are independently available.
     */
    private static Optional<RouteMaintenance> nextMaintainedOwner(FrontierWorldState state, int scanOrdinal) {
        List<RouteMaintenance> values = state.routeMaintenances().values().stream()
                .filter(value -> value.building() || value.status() == RouteMaintenanceStatus.READY)
                .sorted(Comparator.comparing(RouteMaintenance::id)).toList();
        if (values.isEmpty()) return Optional.empty();
        return Optional.of(values.get(Math.floorMod(scanOrdinal - 1, values.size())));
    }

    private static List<ProposedEvent> planBuilding(FrontierWorldState state, RouteMaintenance maintenance,
                                                     ScheduledAction action, Optional<ProposedEvent> retry) {
        if (state.physicalIntents().values().stream().anyMatch(intent -> (intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE
                || intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING)
                && intent.subjectIds().contains(maintenance.id())
                && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING))) return retryOnly(retry);
        if (!EngineeringToolCustody.ready(state, maintenance.team())) {
            if (!EngineeringDepotService.atStations(state, maintenance, EngineeringJourneyPurpose.MUSTER_DEPOT)) {
                return admitDepotJourney(state, maintenance, EngineeringJourneyPurpose.MUSTER_DEPOT, action, retry);
            }
            return EngineeringEquipmentProcess.issueOne(state, maintenance).map(intent -> withRetry(List.of(new ProposedEvent(maintenance.settlementId(),
                    new PhysicalIntentPrepared(intent))), retry)).orElseGet(() -> retryOnly(retry));
        }
        if (maintenance.assembly().isEmpty() || maintenance.assembly().orElseThrow().purpose() == EngineeringJourneyPurpose.MUSTER_DEPOT) {
            return admitAssembly(state, maintenance, action, retry);
        }
        if (!maintenance.assembly().orElseThrow().complete()) return retryOnly(retry);
        if (maintenance.cargoId().isEmpty()) return material(state, maintenance).map(item -> withRetry(List.of(new ProposedEvent(FrontierRouteNetwork.OWNER,
                new PhysicalIntentPrepared(materialLoadingIntent(state, maintenance, item)) )), retry)).orElseGet(() -> retryOnly(retry));
        return retryOnly(retry);
    }

    public static List<ProposedEvent> planAssemblyProgress(FrontierWorldState state, ScheduledAction action) {
        RouteMaintenance maintenance = state.routeMaintenances().get(action.subject());
        if (maintenance == null || !assemblyProgress(maintenance.id(), action.dueAt().ticks()).id().equals(action.id())
                || maintenance.assembly().isEmpty()
                || (!maintenance.building() && maintenance.status() != RouteMaintenanceStatus.READY)) return List.of();
        EngineeringWorkAssembly assembly = maintenance.assembly().orElseThrow();
        if (assembly.complete()) return List.of();
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(assemblyProgress(maintenance.id(), action.dueAt().ticks()
                + state.bootstrap().ruleset().cadence().migrationStepInterval())));
        boolean retainedHotOrRecovery = assembly.purpose() == EngineeringJourneyPurpose.WORKSITE && state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isEngineeringWorksite)
                .anyMatch(lease -> FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(maintenance.id())
                        && lease.status() != SceneLeaseStatus.CLOSED);
        if (retainedHotOrRecovery) return List.of(next);
        SubjectId advancing = assembly.safeAdvances().stream().filter(member -> {
            AmbientActorLease lease = state.ambientLeases().get(member);
            return lease == null || lease.status() == AmbientLeaseStatus.CLOSED;
        }).findFirst().orElse(null);
        if (advancing == null) return List.of(next);
        EngineeringWorkAssembly advanced = assembly.advance(advancing);
        ProposedEvent progressed = advanced.complete() ? new ProposedEvent(maintenance.id(),
                new ScheduleEffect.Created(progress(maintenance, Math.addExact(action.dueAt().ticks(), 1)))) : null;
        return progressed == null ? List.of(new ProposedEvent(FrontierRouteNetwork.OWNER,
                new RouteMaintenanceAssemblyAdvanced(maintenance.id(), advanced)), next) : List.of(
                new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteMaintenanceAssemblyAdvanced(maintenance.id(), advanced)), progressed, next);
    }

    /** Runs a retained owner after a local state transition without re-running strategic admission. */
    public static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        RouteMaintenance maintenance = state.routeMaintenances().get(action.subject());
        if (maintenance == null || !progress(maintenance, action.dueAt().ticks()).id().equals(action.id())) return List.of();
        return maintenance.status() == RouteMaintenanceStatus.READY
                ? planReady(state, maintenance, action, Optional.empty())
                : maintenance.building() ? planBuilding(state, maintenance, action, Optional.empty()) : List.of();
    }

    /** Drives one already-ready operation immediately after its observed repair instead of awaiting the global scan. */
    public static List<ProposedEvent> planReturnProgress(FrontierWorldState state, ScheduledAction action) {
        RouteMaintenance maintenance = state.routeMaintenances().get(action.subject());
        if (maintenance == null || maintenance.status() != RouteMaintenanceStatus.READY
                || !returnProgress(maintenance, action.dueAt().ticks()).id().equals(action.id())) return List.of();
        return planReady(state, maintenance, action, Optional.empty());
    }

    private static List<ProposedEvent> admitAssembly(FrontierWorldState state, RouteMaintenance maintenance, ScheduledAction action, Optional<ProposedEvent> retry) {
        if (maintenance.team().memberIds().stream().map(state.ambientLeases()::get)
                .anyMatch(lease -> lease != null && lease.status() != AmbientLeaseStatus.CLOSED)) return retryOnly(retry);
        EngineeringWorkAssembly assembly;
        try { assembly = EngineeringWorksite.compile(state, maintenance); }
        catch (IllegalArgumentException unavailable) { return retryOnly(retry); }
        return withRetry(List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteMaintenanceAssemblyStarted(maintenance.id(), assembly)),
                new ProposedEvent(SYSTEM, new ScheduleEffect.Created(assemblyProgress(maintenance.id(), action.dueAt().ticks()
                        + state.bootstrap().ruleset().cadence().migrationStepInterval())))), retry);
    }

    private static List<ProposedEvent> admitDepotJourney(FrontierWorldState state, RouteMaintenance maintenance,
                                                           EngineeringJourneyPurpose purpose, ScheduledAction action, Optional<ProposedEvent> retry) {
        EngineeringWorkAssembly current = maintenance.assembly().orElse(null);
        if (current != null) return retryOnly(retry);
        if (maintenance.team().memberIds().stream().map(state.ambientLeases()::get)
                .anyMatch(lease -> lease != null && lease.status() != AmbientLeaseStatus.CLOSED)) return retryOnly(retry);
        EngineeringWorkAssembly journey;
        try { journey = EngineeringDepotService.compile(state, maintenance, purpose); }
        catch (IllegalArgumentException unavailable) { return retryOnly(retry); }
        return withRetry(List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteMaintenanceAssemblyStarted(maintenance.id(), journey)),
                new ProposedEvent(SYSTEM, new ScheduleEffect.Created(assemblyProgress(maintenance.id(), action.dueAt().ticks()
                        + state.bootstrap().ruleset().cadence().migrationStepInterval())))), retry);
    }

    private static Optional<RouteMaintenance> candidate(FrontierWorldState state) {
        if (state.routeMaintenances().size() >= RouteMaintenanceStateSupport.MAX_MAINTENANCE) return Optional.empty();
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        return state.physicalDeltas().values().stream().filter(loss -> loss.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS)
                .filter(loss -> loss.ownerId().equals(Optional.of(FrontierRouteNetwork.OWNER)))
                .filter(loss -> loss.semanticPart().filter(RouteMaintenanceProcess::repairable).isPresent())
                .sorted(Comparator.comparingInt((PhysicalDelta loss) -> loss.position().x()).thenComparingInt(loss -> loss.position().y())
                        .thenComparingInt(loss -> loss.position().z())).filter(loss -> state.routeMaintenances().values().stream()
                        .noneMatch(existing -> existing.repairCell().equals(loss.position()))).map(loss -> admitted(state, loss, assignments)).flatMap(Optional::stream).findFirst();
    }

    /**
     * A PM-owned baseline loss gets first refusal for exact in-place maintenance.
     *
     * <p>The physical observation and strategic patrol are asynchronous, so a replacement may
     * not reserve people or topology while a loss is unassigned or one of its exact repair
     * owners is still viable. A terminal {@link RouteMaintenanceStatus#CONFLICT}, however, is
     * explicit evidence that this particular in-place recovery has failed: it releases its
     * human assignment and permits the route owner's later bounded replan decision. Keeping the
     * historical loss is essential; the replacement compiler must still avoid that scar rather
     * than treating a conflict as a repaired cell.</p>
     */
    static boolean blocksBypassConstruction(FrontierWorldState state, SubjectId settlementId) {
        List<PhysicalDelta> retainedBaselineLosses = state.physicalDeltas().values().stream()
                .filter(loss -> loss.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS)
                .filter(loss -> loss.ownerId().equals(Optional.of(FrontierRouteNetwork.OWNER)))
                .filter(loss -> loss.semanticPart().filter(RouteMaintenanceProcess::repairable).isPresent())
                .filter(loss -> FrontierRouteNetwork.containsOperationSurfaceCell(
                        state.routeTopology().supplyWaypoints(state.bootstrap(), settlementId), loss.position())).toList();
        // No loss means the retry arrived after an observed repair; RouteConstructionProcess
        // closes its no-longer-needed task instead of manufacturing a replacement.
        if (retainedBaselineLosses.isEmpty()) return false;
        for (PhysicalDelta loss : retainedBaselineLosses) {
            RouteMaintenance maintenance = state.routeMaintenances().values().stream()
                    .filter(candidate -> candidate.settlementId().equals(settlementId))
                    .filter(candidate -> candidate.repairCell().equals(loss.position())).findFirst().orElse(null);
            // A missing aggregate is still awaiting the normal maintenance scan. BUILDING and
            // READY retain the same exact cell; only a durable conflict releases it to replan.
            if (maintenance == null || maintenance.status() != RouteMaintenanceStatus.CONFLICT) return true;
        }
        return false;
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
                .filter(item -> state.physicalIntents().values().stream().noneMatch(intent -> intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING
                        && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                        && intent.subjectIds().size() == 5 && intent.subjectIds().get(4).equals(item.id())))
                .sorted(Comparator.comparing(ExactItemStack::id)).findFirst();
    }

    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        RouteMaintenanceStateSupport.validateWorkIntent(state, intent);
        if (transition.status() != PhysicalIntentStatus.CONFIRMED) return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, transition));
        RouteMaintenance maintenance = maintenanceFor(state, intent);
        return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, transition), new ProposedEvent(maintenance.id(),
                new ScheduleEffect.Created(returnProgress(maintenance, Math.addExact(now, 1L)))));
    }

    public static List<ProposedEvent> planMaterialLoadingTransition(FrontierWorldState state, PhysicalIntent intent,
                                                                      PhysicalIntentTransition transition, long now) {
        RouteMaintenanceStateSupport.validateMaterialLoadingIntent(state, intent);
        if (transition.status() != PhysicalIntentStatus.CONFIRMED) return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, transition));
        if (!(transition.observation().orElseThrow() instanceof RouteMaintenanceMaterialLoadObservation observation)) {
            throw new IllegalArgumentException("route maintenance loading requires its exact observation");
        }
        RouteMaintenanceStateSupport.validateMaterialLoadingReceipt(state, intent, observation);
        RouteMaintenance maintenance = state.routeMaintenances().get(observation.maintenanceId());
        if (maintenance == null) throw new IllegalArgumentException("route maintenance loading has no retained operation");
        return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, transition), new ProposedEvent(FrontierRouteNetwork.OWNER,
                new RouteMaintenanceMaterialLoaded(observation.maintenanceId(), new CargoBatch(observation.cargoId(), FrontierRouteNetwork.OWNER,
                        List.of(observation.cargoItemId())))), new ProposedEvent(maintenance.id(), new ScheduleEffect.Created(
                progress(maintenance, Math.addExact(now, 1L)))));
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route maintenance intent must be prepared by the route network");
        if (intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE) RouteMaintenanceStateSupport.validateWorkIntent(state, intent);
        else if (intent.kind() == PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING) RouteMaintenanceStateSupport.validateMaterialLoadingIntent(state, intent);
        else throw new IllegalArgumentException("route maintenance process received foreign intent");
        return state.preparePhysicalIntent(intent);
    }

    private static List<ProposedEvent> planReady(FrontierWorldState state, RouteMaintenance completed,
                                                   ScheduledAction action, Optional<ProposedEvent> retry) {
        if (!EngineeringEquipmentProcess.returnedOrLost(state, completed)) {
            if (!EngineeringDepotService.atStations(state, completed, EngineeringJourneyPurpose.RETURN_DEPOT)) {
                return admitDepotJourney(state, completed, EngineeringJourneyPurpose.RETURN_DEPOT, action, retry);
            }
            return EngineeringEquipmentProcess.returnOne(state, completed).map(intent -> withRetry(List.of(new ProposedEvent(completed.settlementId(),
                    new PhysicalIntentPrepared(intent))), retry)).orElseGet(() -> retryOnly(retry));
        }
        boolean retainedScene = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isEngineeringWorksite)
                .anyMatch(lease -> FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(completed.id())
                        && lease.status() != SceneLeaseStatus.CLOSED);
        if (retainedScene) return retryOnly(retry);
        ProposedEvent closed = new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteMaintenanceClosed(completed.id()));
        return action.kind().equals("frontier.route_maintenance.return_progress") ? List.of(closed) : withRetry(List.of(closed), retry);
    }

    private static List<ProposedEvent> retryOnly(Optional<ProposedEvent> retry) {
        return retry.map(List::of).orElseGet(List::of);
    }

    private static List<ProposedEvent> withRetry(List<ProposedEvent> events, Optional<ProposedEvent> retry) {
        if (retry.isEmpty()) return events;
        java.util.ArrayList<ProposedEvent> result = new java.util.ArrayList<>(events);
        result.add(retry.orElseThrow());
        return List.copyOf(result);
    }

    private static RouteMaintenance maintenanceFor(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.subjectIds().size() < 2) throw new IllegalArgumentException("route maintenance intent lacks its exact project subject");
        RouteMaintenance maintenance = state.routeMaintenances().get(intent.subjectIds().get(1));
        if (maintenance == null || !intent.subjectIds().contains(maintenance.id())) {
            throw new IllegalArgumentException("route maintenance intent has no retained project");
        }
        return maintenance;
    }
}
