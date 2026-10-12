package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Retained sequential equipment visits. Shared storage, geometry, access and transfer own their protocols. */
public final class EngineeringDepotService {
    private static final Set<ResourceStorageRole> WAREHOUSES = Set.of(ResourceStorageRole.WAREHOUSE);
    private EngineeringDepotService() { }

    public static EngineeringWorkAssembly compile(FrontierWorldState state, EngineeringWorkOrder owner,
            EngineeringJourneyPurpose purpose, PhysicalIntentKind operation, long requestedAtTick) {
        requirePurpose(purpose, operation);
        var current = owner.assembly().orElse(null);
        if (current != null && current.equipmentVisit().isPresent() && !current.equipmentVisit().orElseThrow().clearing())
            return clearance(state, owner);
        if (current != null && !current.complete()) throw new IllegalArgumentException("equipment predecessor has not reached its selected endpoint");
        if (current != null && current.equipmentVisit().isPresent()
                && !current.equipmentVisit().orElseThrow().access().accessBoundary().cleared(
                    state.actorLocations().get(current.equipmentVisit().orElseThrow().actorId()).body()))
            throw new IllegalArgumentException("equipment predecessor has not freed its service boundary");
        for (var actor : owner.engineeringTeam().orElseThrow().memberIds()) {
            if (operation == PhysicalIntentKind.EQUIPMENT_ISSUE) {
                if (EngineeringToolCustody.holdsTool(state, actor)) continue;
                var origin = state.actorLocations().get(actor).supportingSurface();
                for (var item : ResourceStorageSelection.exactSources(state, owner.settlementId(), EngineeringToolCustody.FIRST_GRAYBOX_TOOL,
                        1, origin, WAREHOUSES)) {
                    if (reserved(state, item.id(), actor)) continue;
                    var slot = (InventoryCustody.ContainerSlot) item.custody();
                    var access = ResourceStorageAccess.at(state, slot.containerId());
                    var visit = new EngineeringEquipmentVisit(actor, item.id(), access, operation, Optional.empty(),
                            EngineeringEquipmentVisit.Phase.SERVICE, requestedAtTick);
                    if (!available(state, owner, visit)) continue;
                    try { return selectRoute(state, owner, purpose, visit, access.stations()); }
                    catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                        if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
                    }
                }
            } else {
                for (var item : state.inventory().actorItems(actor).stream().filter(value -> EngineeringToolCustody.isTool(value.itemKind()))
                        .filter(value -> value.economicOwnerId().equals(owner.settlementId())).sorted(Comparator.comparing(ExactItemStack::id)).toList()) {
                    if (reserved(state, item.id(), actor)) continue;
                    for (var endpoint : ResourceStorageSelection.receivingCandidates(state, owner.settlementId(), item.itemKind(), item.count(),
                            state.actorLocations().get(actor).supportingSurface(), WAREHOUSES, Optional.empty())) {
                        var target = state.firstFreeContainerSlot(endpoint.containerId());
                        if (target.isEmpty()) continue;
                        var access = ResourceStorageAccess.at(state, endpoint.containerId());
                        var visit = new EngineeringEquipmentVisit(actor, item.id(), access, operation,
                                Optional.of(new PhysicalContainerSlot(access.containerId(), target.orElseThrow())),
                                EngineeringEquipmentVisit.Phase.SERVICE, requestedAtTick);
                        if (!available(state, owner, visit)) continue;
                        try { return selectRoute(state, owner, purpose, visit, access.stations()); }
                        catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                            if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
                        }
                    }
                }
            }
        }
        throw new KnownPedestrianNavigation.RouteUnavailable("no available selected engineering equipment visit");
    }

    private static EngineeringWorkAssembly clearance(FrontierWorldState state, EngineeringWorkOrder owner) {
        var prior = owner.assembly().orElseThrow(); var visit = prior.equipmentVisit().orElseThrow();
        if (pending(state, owner, visit)) throw new IllegalArgumentException("equipment visit retains an unresolved transfer");
        boolean transferred = transferred(state, owner, visit);
        if (!transferred && available(state, owner, visit))
            throw new IllegalArgumentException("equipment service is still needed before clearing");
        if (transferred && !prior.complete()) throw new IllegalArgumentException("equipment receipt cannot complete an unarrived visit");
        var origin = state.actorLocations().get(visit.actorId()).supportingSurface();
        var point = ServiceBoundaryComposition.geometry(state, ServiceBoundaryComposition.declaration(state, visit.access().containerId()).identity());
        var exits = ServiceClearanceTargets.waitingTargets(point, origin, otherPositions(state, owner, visit.actorId()));
        if (exits.isEmpty()) throw new KnownPedestrianNavigation.RouteUnavailable("equipment visit has no supported clearance target");
        return selectRoute(state, owner, prior.purpose(), visit.clearing(transferred), exits);
    }

    private static EngineeringWorkAssembly selectRoute(FrontierWorldState state, EngineeringWorkOrder owner,
            EngineeringJourneyPurpose purpose, EngineeringEquipmentVisit visit, List<SurfaceAnchor> targets) {
        var origin = state.actorLocations().get(visit.actorId()).supportingSurface();
        var knowledge = knowledge(state, visit.access()).withTaskExclusions(otherPositions(state, owner, visit.actorId()));
        var execution = EngineeringExecutionAuthority.assemblyCurrent(state, owner).requireMember(visit.actorId());
        List<SurfaceAnchor> path = null;
        for (int offset = 0; offset < targets.size(); offset += MovementOrder.MAX_LEGAL_STATIONS) {
            var batch = targets.subList(offset, Math.min(targets.size(), offset + MovementOrder.MAX_LEGAL_STATIONS));
            var order = new MovementOrder(owner.id(), visit.actorId(), execution.generation(), 1L, batch,
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
            try { path = knowledge.plannedPath(origin, order); break; }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
            }
        }
        if (path == null) throw new KnownPedestrianNavigation.RouteUnavailable("no known route to selected equipment access");
        if (visit.clearing() && !visit.access().accessBoundary().allowsWaitingRoute(path))
            throw new IllegalArgumentException("equipment clearance re-enters the service boundary");
        if (visit.clearing()) requireClearanceTarget(state, owner, visit, origin, path.getLast());
        var members = new LinkedHashMap<SubjectId, EngineeringWorkAssembly.Member>();
        for (var actor : owner.engineeringTeam().orElseThrow().memberIds()) {
            var route = actor.equals(visit.actorId()) ? path : List.of(state.actorLocations().get(actor).supportingSurface());
            members.put(actor, new EngineeringWorkAssembly.Member(route.stream().map(SurfaceAnchor::support).toList(), 0));
        }
        return new EngineeringWorkAssembly(purpose, members, Optional.of(visit));
    }

    /** Replay validates chosen preimages/route/protocol; it never discovers or reranks a warehouse. */
    public static void requireSuccessor(FrontierWorldState state, EngineeringWorkOrder owner, EngineeringWorkAssembly selected) {
        if (!EngineeringExecutionAuthority.mayStartJourney(state, owner))
            throw new IllegalArgumentException("new engineering journey cannot replace physically held crew");
        var current = owner.assembly().orElse(null);
        selected.requirePredecessor(owner.assembly());
        if (current != null && current.equipmentVisit().filter(EngineeringEquipmentVisit::clearing).isPresent()
                && !current.equipmentVisit().orElseThrow().access().accessBoundary().cleared(
                    state.actorLocations().get(current.equipmentVisit().orElseThrow().actorId()).body()))
            throw new IllegalArgumentException("engineering successor has not freed the retained access boundary");
        if (selected.purpose() == EngineeringJourneyPurpose.WORKSITE) {
            if (!owner.building() || !EngineeringToolCustody.ready(state, owner.engineeringTeam().orElseThrow())
                    || current != null && (!current.complete() || current.equipmentVisit().isEmpty()
                        || !current.equipmentVisit().orElseThrow().clearing()))
                throw new IllegalArgumentException("worksite successor lacks a cleared equipped predecessor");
        } else {
            var visit = selected.equipmentVisit().orElseThrow();
            if (!visit.access().settlementId().equals(owner.settlementId()))
                throw new IllegalArgumentException("equipment visit differs from its declared selected storage");
            if (visit.clearing()) {
                if (current == null || current.equipmentVisit().isEmpty()
                        || !current.equipmentVisit().orElseThrow().clearing(
                            visit.phase() == EngineeringEquipmentVisit.Phase.CLEARING_TRANSFERRED).equals(visit)
                        || pending(state, owner, visit)
                        || visit.phase() == EngineeringEquipmentVisit.Phase.CLEARING_TRANSFERRED && !current.complete()
                        || visit.phase() == EngineeringEquipmentVisit.Phase.CLEARING_TRANSFERRED != transferred(state, owner, visit)
                        || visit.phase() == EngineeringEquipmentVisit.Phase.CLEARING_UNAVAILABLE && available(state, owner, visit))
                    throw new IllegalArgumentException("equipment clearance lacks its settled or disrupted exact predecessor");
            } else {
                if (current != null && (!current.complete() || current.equipmentVisit().isEmpty()
                        || !current.equipmentVisit().orElseThrow().clearing()))
                    throw new IllegalArgumentException("equipment visit requires a cleared predecessor");
                if (!visit.access().equals(ResourceStorageAccess.at(state, visit.access().containerId()))
                        || current != null && visit.requestedAtTick() < current.equipmentVisit().orElseThrow().requestedAtTick()
                        || current != null && current.equipmentVisit().filter(previous -> previous.actorId().equals(visit.actorId())
                            && previous.itemId().equals(visit.itemId()) && previous.operation() == visit.operation()
                            && previous.requestedAtTick() == visit.requestedAtTick()).isPresent()
                        || !available(state, owner, visit)
                        || pending(state, owner, visit) || reserved(state, visit.itemId(), visit.actorId()))
                    throw new IllegalArgumentException("selected equipment visit lacks its exact available resource");
            }
            var route = selected.members().get(visit.actorId()).corridor().stream().map(SurfaceAnchor::new).toList();
            knowledge(state, visit.access()).withTaskExclusions(otherPositions(state, owner, visit.actorId())).requireRoute(route);
            if (visit.clearing() && !visit.access().accessBoundary().allowsWaitingRoute(route))
                throw new IllegalArgumentException("selected equipment clearance re-enters access");
            if (visit.clearing()) requireClearanceTarget(state, owner, visit, route.getFirst(), route.getLast());
        }
        for (var entry : selected.members().entrySet()) {
            if (entry.getValue().cursor() != 0 || entry.getValue().spatial().pending()
                    || !entry.getValue().currentSurface().equals(state.actorLocations().get(entry.getKey()).supportingSurface()))
                throw new IllegalArgumentException("selected engineering journey differs from its actual exact origin");
        }
    }

    public static boolean available(FrontierWorldState state, EngineeringWorkOrder owner, EngineeringEquipmentVisit visit) {
        var item = state.inventory().items().get(visit.itemId());
        if (item == null || !item.economicOwnerId().equals(owner.settlementId()) || !EngineeringToolCustody.isTool(item.itemKind())
                || !ResourceStorageSelection.permits(state.inventory(), visit.access().containerId(), owner.settlementId(), item.itemKind(), WAREHOUSES)
                || ReferenceContainerCustody.blocksCanonicalUse(state, visit.access().containerId())) return false;
        if (visit.operation() == PhysicalIntentKind.EQUIPMENT_ISSUE)
            return owner.building() && !EngineeringToolCustody.holdsTool(state, visit.actorId())
                    && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(visit.access().containerId());
        var slot = visit.returnSlot().orElseThrow();
        return owner.readyForToolReturn() && item.custody().equals(new InventoryCustody.Actor(visit.actorId()))
                && slot.slot() < state.inventory().containers().get(slot.containerId()).slotCount()
                && state.containerSlotAvailable(new InventoryCustody.ContainerSlot(slot.containerId(), slot.slot()));
    }

    public static boolean transferred(FrontierWorldState state, EngineeringWorkOrder owner, EngineeringEquipmentVisit visit) {
        var transferId = visit.transferId(owner.id(), EngineeringExecutionAuthority.assemblyCurrent(state, owner).requireMember(visit.actorId()).generation());
        return state.physicalIntents().values().stream().filter(intent -> intent.lifecycleOwner() == PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE
                && intent.kind() == visit.operation() && intent.status() == PhysicalIntentStatus.CONFIRMED)
                .anyMatch(intent -> intent.id().equals(transferId) && intent.roles().require(PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER).equals(owner.id())
                        && intent.roles().require(PhysicalIntentSubjectRole.ENGINEERING_WORKER).equals(visit.actorId())
                        && intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT).equals(visit.itemId())
                        && EquipmentPhysicalAuthority.container(intent).equals(visit.access().containerId()));
    }

    public enum VisitProgress { NONE, APPROACHING, TRANSFER_READY, TRANSFER_PENDING, CLEAR_REQUIRED, CLEARING, CLEARED }
    public static boolean clearedForCompletion(FrontierWorldState state, EngineeringWorkOrder owner) {
        var assembly = owner.assembly().orElse(null);
        var visit = assembly == null ? null : assembly.equipmentVisit().orElse(null);
        return visit == null || visit.clearing() && assembly.complete()
                && visit.access().accessBoundary().cleared(state.actorLocations().get(visit.actorId()).body());
    }
    /** Exact retained visit outcome, not a second stock or access selector. */
    public static VisitProgress progress(FrontierWorldState state, EngineeringWorkOrder owner) {
        var assembly = owner.assembly().orElse(null);
        var visit = assembly == null ? null : assembly.equipmentVisit().orElse(null);
        if (visit == null) return VisitProgress.NONE;
        if (visit.clearing()) return assembly.complete() ? VisitProgress.CLEARED : VisitProgress.CLEARING;
        if (pending(state, owner, visit)) return VisitProgress.TRANSFER_PENDING;
        if (transferred(state, owner, visit) || !available(state, owner, visit)) return VisitProgress.CLEAR_REQUIRED;
        return assembly.complete() ? VisitProgress.TRANSFER_READY : VisitProgress.APPROACHING;
    }

    private static boolean pending(FrontierWorldState state, EngineeringWorkOrder owner, EngineeringEquipmentVisit visit) {
        return state.physicalIntents().values().stream().filter(intent -> intent.lifecycleOwner() == PhysicalIntentLifecycleOwner.ENGINEERING_WORKSITE
                && (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
                && intent.status() != PhysicalIntentStatus.CONFIRMED)
                .anyMatch(intent -> intent.roles().require(PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER).equals(owner.id())
                        && intent.roles().require(PhysicalIntentSubjectRole.ENGINEERING_WORKER).equals(visit.actorId()));
    }

    private static boolean reserved(FrontierWorldState state, SubjectId item, SubjectId actor) {
        return state.physicalIntents().values().stream().filter(intent -> intent.status() != PhysicalIntentStatus.CONFIRMED
                && (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN))
                .anyMatch(intent -> intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT).equals(item)
                        || EquipmentServiceBoundary.capability(intent).actorId(intent).equals(actor));
    }

    static KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, ResourceStorageAccess access) {
        return ServiceBoundaryComposition.knowledge(state, ServiceBoundaryComposition.declaration(state, access.containerId()).identity());
    }
    private static void requireClearanceTarget(FrontierWorldState state, EngineeringWorkOrder owner,
            EngineeringEquipmentVisit visit, SurfaceAnchor origin, SurfaceAnchor target) {
        var point = ServiceBoundaryComposition.geometry(state, ServiceBoundaryComposition.declaration(state, visit.access().containerId()).identity());
        if (!ServiceClearanceTargets.waitingTargets(point, origin, otherPositions(state, owner, visit.actorId())).contains(target))
            throw new IllegalArgumentException("equipment clearance does not free the immediate egress");
    }
    private static Set<SurfaceAnchor> otherPositions(FrontierWorldState state, EngineeringWorkOrder owner, SubjectId actor) {
        return owner.engineeringTeam().orElseThrow().memberIds().stream().filter(id -> !id.equals(actor))
                .map(id -> state.actorLocations().get(id).supportingSurface()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static void requirePurpose(EngineeringJourneyPurpose purpose, PhysicalIntentKind operation) {
        if (purpose != EngineeringJourneyPurpose.MUSTER_DEPOT && purpose != EngineeringJourneyPurpose.RETURN_DEPOT
                || purpose == EngineeringJourneyPurpose.MUSTER_DEPOT && operation != PhysicalIntentKind.EQUIPMENT_ISSUE
                || purpose == EngineeringJourneyPurpose.RETURN_DEPOT && operation != PhysicalIntentKind.EQUIPMENT_RETURN)
            throw new IllegalArgumentException("equipment journey requires its compatible explicit purpose and operation");
    }
    public static boolean atStations(FrontierWorldState state, EngineeringWorkOrder owner, EngineeringJourneyPurpose purpose) {
        var assembly = owner.assembly().orElse(null);
        if (assembly == null || assembly.purpose() != purpose || !assembly.complete()) return false;
        var visit = assembly.equipmentVisit().orElse(null);
        return visit != null && !visit.clearing()
                && visit.access().stations().contains(state.actorLocations().get(visit.actorId()).supportingSurface())
                && ServiceAccessCoordinator.available(state, EngineeringEquipmentServiceAccess.identity(owner, visit));
    }
    static void validate(FrontierBootstrap bootstrap, EngineeringWorkOrder owner, EngineeringWorkAssembly journey) {
        var visit = journey.equipmentVisit().orElseThrow();
        if (!visit.access().settlementId().equals(owner.settlementId())
                || !owner.engineeringTeam().orElseThrow().memberIds().contains(visit.actorId()))
            throw new IllegalArgumentException("equipment visit changes its declared settlement or crew");
        for (var member : journey.members().values()) {
            for (var surface : member.corridor()) FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), surface);
            member.spatial().waitingOrigin().ifPresent(origin -> FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), origin.support()));
            member.rejoin().ifPresent(approach -> approach.path().forEach(surface -> FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), surface.support())));
        }
    }
}
