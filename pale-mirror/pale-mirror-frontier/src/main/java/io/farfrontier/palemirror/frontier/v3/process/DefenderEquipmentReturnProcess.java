package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentReturnStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentServiceRequest;
import io.farfrontier.palemirror.frontier.v3.model.ServiceAccessDemand;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultStatus;
import io.farfrontier.palemirror.frontier.v3.model.ResourceStorageSelection;
import io.farfrontier.palemirror.frontier.v3.model.ResourceStorageRole;
import io.farfrontier.palemirror.frontier.v3.model.ResourceStorageAccess;
import io.farfrontier.palemirror.frontier.v3.model.ContainerPhysicalAuthorityComposition;
import io.farfrontier.palemirror.frontier.v3.model.StableCorrelationIdentity;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentPhysicalAuthority;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.DefenderEquipmentReturnVisit;
import io.farfrontier.palemirror.frontier.v3.model.DefenderEquipmentReturnVisitChanged;
import io.farfrontier.palemirror.frontier.v3.model.EquipmentServiceBoundary;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Bounded settlement-owned review that returns one exact released defender stack at a time. */
public final class DefenderEquipmentReturnProcess {
    public static final String REVIEW_ACTION = DefenderEquipmentReturnContinuation.REVIEW;
    private DefenderEquipmentReturnProcess() { }

    public static boolean mayCompact(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN && intent.status() == PhysicalIntentStatus.CONFIRMED) {
            var assault = state.strategicPlans().settlementAssaults().get(intent.roles().require(PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT));
            if (assault != null && assault.equipmentReturnVisit().filter(visit -> visit.transferId().equals(intent.id())).isPresent()) return false;
        }
        return EquipmentItemDeparture.mayCompact(state, intent);
    }

    public static ScheduledAction review(SettlementAssault assault, long dueAt) {
        return DefenderEquipmentReturnContinuation.review(assault, dueAt);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        if (!REVIEW_ACTION.equals(action.kind())) throw new IllegalArgumentException("equipment return rejects a foreign scheduled kind");
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(action.subject());
        if (assault == null || !action.id().equals(review(assault, action.dueAt().ticks()).id()))
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        List<ProposedEvent> events = new ArrayList<>();
        if (assault.equipmentReturnVisit().isEmpty()) {
            Optional<PhysicalIntent> intent = returnOne(state, assault, action.dueAt().ticks());
            intent.ifPresent(value -> events.add(new ProposedEvent(assault.id(), new DefenderEquipmentReturnVisitChanged(assault.id(), Optional.empty(),
                    Optional.of(new DefenderEquipmentReturnVisit(value.id(), value.roles(), ResourceStorageAccess.at(state, EquipmentPhysicalAuthority.container(value)),
                            value.targetSlot().orElseThrow(), value.equipmentServiceRequest().orElseThrow()))))));
        } else {
            try { events.addAll(planVisit(state, assault, action.dueAt().ticks())); }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                // Real cooperative selection retains its addressed calculation dependency.
                // Waiting is not a missing warehouse or a terminal stock loss.
            }
        }
        if (assault.status() != SettlementAssaultStatus.RESOLVED || hasPendingOrReturnable(state, assault)) {
            var next = review(assault, Math.addExact(action.dueAt().ticks(), events.isEmpty()
                    ? state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval() : 1L));
            events.add(new ProposedEvent(assault.id(), new ScheduleEffect.Rescheduled(action.id(), next)));
        } else {
            events.add(new ProposedEvent(assault.id(), new ScheduleEffect.Cancelled(action.id())));
        }
        return List.copyOf(events);
    }

    private static ProposedEvent changed(SettlementAssault parent, Optional<DefenderEquipmentReturnVisit> next) {
        return new ProposedEvent(parent.id(), new DefenderEquipmentReturnVisitChanged(parent.id(), parent.equipmentReturnVisit(), next));
    }
    private static DefenderEquipmentReturnVisit phase(DefenderEquipmentReturnVisit visit, DefenderEquipmentReturnVisit.Phase next,
            ActorExecutionId execution, Optional<MovementOrder> order) {
        return new DefenderEquipmentReturnVisit(visit.transferId(), visit.roles(), visit.access(), visit.targetSlot(), visit.request(), next, Optional.of(execution), order);
    }
    private static MovementOrder goal(DefenderEquipmentReturnVisit visit, java.util.List<SurfaceAnchor> stations, long tick) {
        return new MovementOrder(visit.ownerId(), visit.actorId(), 0, Math.addExact(tick, 1L), stations,
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
    }
    private static boolean mayStartActivity(FrontierWorldState state, SubjectId actor) {
        var lease = state.ambientLeases().get(actor);
        return ActorExecutionComposition.LIFECYCLE.mayAdmitVacant(state, actor)
                && !HumanAssignmentProjection.compile(state).assignment(actor).active()
                && !state.humanPopulation().meals().containsKey(actor) && !state.actorMovements().containsKey(actor)
                && !ActorExecutionCoordinator.sceneOwns(state, actor)
                && (lease == null || lease.status() == AmbientLeaseStatus.HOT || lease.status() == AmbientLeaseStatus.CLOSED);
    }
    private static List<ProposedEvent> planVisit(FrontierWorldState state, SettlementAssault parent, long tick) {
        var visit = parent.equipmentReturnVisit().orElseThrow();
        var intent = state.physicalIntents().get(visit.transferId());
        var cancellation = state.fencedRecovery().tombstones().get(FencedRecoveryPhysicalIntentSupport.bindingId(selectedIntent(state, parent, visit)));
        if (intent != null && intent.status() == PhysicalIntentStatus.CONFLICTED
                && PhysicalIntentResolvedRetentionPolicy.cancelledBeforeEffect().mayCompact(state, intent)
                || intent == null && cancellation != null && cancellation.disposition() == FencedRecoveryDisposition.CANCELLED_BEFORE_EFFECT)
            return List.of(changed(parent, Optional.empty()));
        if (intent == null && !selectedAvailable(state, visit)) return List.of(changed(parent, Optional.empty()));
        var actual = state.actorLocations().get(visit.actorId()).supportingSurface();
        if (visit.phase() == DefenderEquipmentReturnVisit.Phase.SELECTED) {
            if (!mayStartActivity(state, visit.actorId())) return List.of();
            var execution = state.actorExecutions().next(visit.actorId(), ActorActivityKind.EQUIPMENT_SERVICE, visit.ownerId());
            return List.of(changed(parent, Optional.of(phase(visit, DefenderEquipmentReturnVisit.Phase.APPROACH, execution,
                    Optional.of(goal(visit, visit.access().stations(), tick))))));
        }
        var execution = visit.execution().orElseThrow();
        if (!state.actorExecutions().owns(visit.actorId(), ActorActivityKind.EQUIPMENT_SERVICE, visit.ownerId())) return List.of();
        if (visit.phase() == DefenderEquipmentReturnVisit.Phase.SERVICE) {
            if (intent == null) {
                if (!visit.access().stations().contains(actual)) return List.of(changed(parent, Optional.of(phase(visit,
                        DefenderEquipmentReturnVisit.Phase.APPROACH, execution, Optional.of(goal(visit, visit.access().stations(), tick))))));
                return returnOne(state, parent, tick).map(value -> List.of(new ProposedEvent(parent.settlementId(), new PhysicalIntentPrepared(value)))).orElseGet(List::of);
            }
            if (intent.status() != PhysicalIntentStatus.CONFIRMED) return List.of();
            if (visit.access().accessBoundary().cleared(actual.standingBody())) return List.of(changed(parent, Optional.empty()));
            var excluded = new java.util.HashSet<SurfaceAnchor>();
            state.actorLocations().entrySet().stream().filter(entry -> !entry.getKey().equals(visit.actorId()))
                    .filter(entry -> entry.getValue().condition().status() == ActorLifeStatus.ALIVE)
                    .map(entry -> entry.getValue().supportingSurface()).forEach(excluded::add);
            state.actorMovements().values().stream().flatMap(move -> move.order().legalStations().stream()).forEach(excluded::add);
            state.humanPopulation().meals().values().stream().map(ResidentMeal::clearingSurface).forEach(excluded::add);
            var destination = ServiceAreaDestinations.select(ServiceBoundaryComposition.geometries(state), visit.actorId(), actual,
                    ResourceStorageNavigation.forEndpoint(state, visit.access().endpoint()), excluded);
            return destination.map(target -> List.of(changed(parent, Optional.of(phase(visit, DefenderEquipmentReturnVisit.Phase.CLEARANCE,
                    execution, Optional.of(goal(visit, List.of(target), tick))))))).orElseGet(List::of);
        }
        if (state.actorMovements().containsKey(visit.actorId())) return List.of();
        if (visit.order().orElseThrow().arrivedAt(actual)) {
            if (visit.phase() == DefenderEquipmentReturnVisit.Phase.CLEARANCE) return List.of(changed(parent, Optional.empty()));
            return List.of(changed(parent, Optional.of(phase(visit, DefenderEquipmentReturnVisit.Phase.SERVICE, execution, Optional.empty()))));
        }
        var movement = new ActorMovement(visit.order().orElseThrow(), tick,
                new ActorMovementContext.DefenderEquipmentReturn(visit.ownerId(), visit.transferId()), execution);
        return List.of(new ProposedEvent(visit.actorId(), new ActorMovementStarted(movement)),
                new ProposedEvent(visit.actorId(), new ScheduleEffect.Created(ActorMovementProcess.progress(movement, Math.addExact(tick, 1L)))));
    }

    private static Optional<PhysicalIntent> returnOne(FrontierWorldState state, SettlementAssault assault, long requestedAt) {
        if (assault.status() != SettlementAssaultStatus.RESOLVED) return Optional.empty();
        if (assault.equipmentReturnVisit().isPresent()) {
            var selected = assault.equipmentReturnVisit().orElseThrow();
            if (selected.phase() != DefenderEquipmentReturnVisit.Phase.SERVICE
                    || state.physicalIntents().containsKey(selected.transferId()) || !selectedAvailable(state, selected)
                    || !selected.access().stations().contains(state.actorLocations().get(selected.actorId()).supportingSurface())) return Optional.empty();
            var intent = selectedIntent(state, assault, selected);
            EquipmentReturnStateSupport.validateIntent(state, intent);
            return Optional.of(intent);
        }
        if (state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN
                && intent.lifecycleOwner() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT
                && intent.status() != PhysicalIntentStatus.CONFIRMED && intent.roles().require(PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT).equals(assault.id()))) return Optional.empty();
        ExactItemStack item = assault.defenderIds().stream().sorted()
                .filter(resident -> state.actorLocations().get(resident).condition().status() == ActorLifeStatus.ALIVE
                        && !EquipmentPhysicalAuthority.actorCommitted(state, resident)
                        && state.strategicPlans().settlementAssaults().values().stream().noneMatch(parent ->
                            parent.equipmentReturnVisit().filter(visit -> visit.actorId().equals(resident)).isPresent()))
                .flatMap(resident -> state.inventory().actorItems(resident).stream())
                .filter(value -> value.economicOwnerId().equals(assault.settlementId()))
                .filter(value -> HumanTacticalFunctionProjection.isGrayboxWeaponKind(value.itemKind()))
                .min(Comparator.comparing(ExactItemStack::id)).orElse(null);
        if (item == null || !(item.custody() instanceof InventoryCustody.Actor actor)) return Optional.empty();
        var origin = state.actorLocations().get(actor.actorId()).supportingSurface();
        var target = ResourceStorageSelection.receivingCandidates(state, assault.settlementId(), item.itemKind(), item.count(),
                origin, java.util.Set.of(ResourceStorageRole.WAREHOUSE), Optional.empty()).stream().filter(endpoint -> {
                    return !ReferenceContainerCustody.blocksCanonicalUse(state, endpoint.containerId())
                            && !ContainerPhysicalAuthorityComposition.pending(state, endpoint.containerId())
                            && state.firstFreeContainerSlot(endpoint.containerId()).isPresent();
                }).findFirst().orElse(null);
        if (target == null) return Optional.empty();
        SubjectId container = target.containerId();
        ResourceStorageAccess.at(state, container);
        var body = state.actorLocations().get(actor.actorId()).body();
        int slot = state.firstFreeContainerSlot(container).orElseThrow();
        String suffix = StableCorrelationIdentity.digest(assault.id().value() + "|" + actor.actorId().value() + "|"
                + item.id().value() + "|" + container.value() + "|" + slot + "|" + requestedAt);
        var intent = new PhysicalIntent(new PhysicalIntentId("intent:equipment-return-" + suffix), PhysicalIntentKind.EQUIPMENT_RETURN,
                PhysicalIntentStatus.PREPARED, assault.settlementId(), PhysicalIntentRoleBinding.assaultEquipmentReturn(assault.id(), actor.actorId(), item.id(), container),
                new FixedPosition(FixedScalar.whole(body.x()), FixedScalar.whole(body.y()), FixedScalar.whole(body.z())),
                0, PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED,
                new PhysicalContainerSlot(container, slot), new EquipmentServiceRequest(
                        new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT, assault.id(), actor.actorId(), container), requestedAt),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
        EquipmentReturnStateSupport.validateIntent(state, intent);
        return Optional.of(intent);
    }

    private static PhysicalIntent selectedIntent(FrontierWorldState state, SettlementAssault assault, DefenderEquipmentReturnVisit visit) {
        var body = state.actorLocations().get(visit.actorId()).body();
        return new PhysicalIntent(visit.transferId(), PhysicalIntentKind.EQUIPMENT_RETURN, PhysicalIntentStatus.PREPARED,
                assault.settlementId(), visit.roles(), new FixedPosition(FixedScalar.whole(body.x()), FixedScalar.whole(body.y()), FixedScalar.whole(body.z())),
                0, PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED, visit.targetSlot(), visit.request(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
    }

    public static boolean selectedAvailable(FrontierWorldState state, DefenderEquipmentReturnVisit visit) {
        var actor = state.actorLocations().get(visit.actorId());
        var item = state.inventory().items().get(visit.itemId());
        var container = state.inventory().containers().get(visit.access().containerId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || item == null
                || !item.custody().equals(new InventoryCustody.Actor(visit.actorId())) || container == null
                || visit.targetSlot().slot() >= container.slotCount() || ReferenceContainerCustody.blocksCanonicalUse(state, container.id())
                || !ResourceStorageSelection.permits(state.inventory(), container.id(), item.economicOwnerId(), item.itemKind(), java.util.Set.of(ResourceStorageRole.WAREHOUSE))
                || state.inventory().itemAt(container.id(), visit.targetSlot().slot()).isPresent()
                || !state.containerSlotAvailableForClaim(new InventoryCustody.ContainerSlot(container.id(), visit.targetSlot().slot()),
                        new io.farfrontier.palemirror.frontier.v3.model.ContainerSlotClaim.Owner(
                                io.farfrontier.palemirror.frontier.v3.model.ContainerSlotClaim.Family.EQUIPMENT, visit.ownerId()))) return false;
        return visit.access().equals(ResourceStorageAccess.at(state, container.id()));
    }

    /** Exact declaration validation only; the reducer never invokes selection or navigation. */
    public static FrontierWorldState reduceVisit(FrontierWorldState state, SubjectId subject, DefenderEquipmentReturnVisitChanged changed, long tick) {
        var assault = state.strategicPlans().settlementAssaults().get(changed.assaultId());
        if (assault == null || !subject.equals(assault.id()) || assault.status() != SettlementAssaultStatus.RESOLVED
                || !assault.equipmentReturnVisit().equals(changed.predecessor()))
            throw new IllegalArgumentException("equipment return visit has a stale or foreign parent declaration");
        if (changed.selected().isPresent()) {
            var selected = changed.selected().orElseThrow();
            var update = FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().replaceSettlementAssault(
                    assault.withEquipmentReturnVisit(changed.selected())));
            if (changed.predecessor().isEmpty()) {
                if (selected.phase() != DefenderEquipmentReturnVisit.Phase.SELECTED || !selectedAvailable(state, selected))
                    throw new IllegalArgumentException("selected return visit is unavailable or claims premature authority");
                EquipmentServiceBoundary.validateIntent(state, selectedIntent(state, assault, selected));
                return state.withChanges(update);
            }
            var previous = changed.predecessor().orElseThrow();
            if (!previous.transferId().equals(selected.transferId()) || !previous.roles().equals(selected.roles())
                    || !previous.access().equals(selected.access()) || !previous.targetSlot().equals(selected.targetSlot())
                    || !previous.request().equals(selected.request())) throw new IllegalArgumentException("equipment continuation substituted its exact selected declaration");
            if (previous.phase() == DefenderEquipmentReturnVisit.Phase.SELECTED) {
                if (selected.phase() != DefenderEquipmentReturnVisit.Phase.APPROACH || !selectedAvailable(state, selected)
                        || !mayStartActivity(state, selected.actorId()))
                    throw new IllegalArgumentException("equipment approach lacks exact vacant activity admission");
                return ResidentActivityProcess.retargetHotResident(ActorExecutionComposition.LIFECYCLE.prepareVacant(state,
                        selected.execution().orElseThrow()).commit(state, update), selected.actorId(), tick);
            }
            if (!previous.execution().equals(selected.execution())) throw new IllegalArgumentException("equipment phase substituted its current activity generation");
            state.actorExecutions().requireCurrent(previous.execution().orElseThrow());
            if (state.actorMovements().containsKey(previous.actorId())) throw new IllegalArgumentException("equipment phase changed before its exact movement settled");
            var actual = state.actorLocations().get(previous.actorId()).supportingSurface();
            boolean valid = switch (previous.phase()) {
                case APPROACH -> selected.phase() == DefenderEquipmentReturnVisit.Phase.SERVICE && previous.order().orElseThrow().arrivedAt(actual);
                case SERVICE -> selected.phase() == DefenderEquipmentReturnVisit.Phase.APPROACH
                        ? !state.physicalIntents().containsKey(previous.transferId()) && !previous.access().stations().contains(actual) && selectedAvailable(state, selected)
                        : selected.phase() == DefenderEquipmentReturnVisit.Phase.CLEARANCE
                            && Optional.ofNullable(state.physicalIntents().get(previous.transferId())).filter(intent -> intent.status() == PhysicalIntentStatus.CONFIRMED).isPresent();
                case SELECTED, CLEARANCE -> false;
            };
            if (!valid) throw new IllegalArgumentException("equipment phase lacks its exact arrival or confirmed resource boundary");
            if (selected.phase() == DefenderEquipmentReturnVisit.Phase.CLEARANCE) {
                var target = selected.order().orElseThrow().legalStations();
                var knowledge = ResourceStorageNavigation.forEndpoint(state, selected.access().endpoint());
                for (var station : target) {
                    knowledge.requireRoute(List.of(station));
                    if (ServiceBoundaryComposition.geometries(state).stream().anyMatch(point -> ServiceAreaDestinations.temporary(point, station)))
                        throw new IllegalArgumentException("equipment clearance selected another temporary service buffer");
                }
            }
            return ResidentActivityProcess.retargetHotResident(state.withChanges(update), selected.actorId(), tick);
        } else {
            var visit = changed.predecessor().orElseThrow();
            var intent = state.physicalIntents().get(visit.transferId());
            if (intent != null && intent.status() == PhysicalIntentStatus.CONFLICTED
                    && PhysicalIntentResolvedRetentionPolicy.cancelledBeforeEffect().mayCompact(state, intent)) {
                state = FrontierWorldProcessCatalog.physicalLifecycles().compactCancelledBeforeEffect(state, intent);
                intent = null;
            }
            if (intent != null && intent.status() != PhysicalIntentStatus.CONFIRMED)
                throw new IllegalArgumentException("equipment visit cannot retire its unresolved physical transfer");
            if (intent != null && !visit.access().accessBoundary().cleared(ActorMovementProcess.bodyAt(state, visit.actorId(), tick)))
                throw new IllegalArgumentException("confirmed equipment transfer has not cleared its access footprint");
            var update = FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().replaceSettlementAssault(
                    assault.withEquipmentReturnVisit(Optional.empty())));
            if (visit.execution().isPresent()) update.actorExecutions(ActorExecutionComposition.LIFECYCLE.retire(state, visit.actorId(), ActorActivityKind.EQUIPMENT_SERVICE, visit.ownerId()));
            var movement = state.actorMovements().get(visit.actorId());
            var retired = movement != null && visit.execution().equals(Optional.of(movement.executionId()))
                    ? ActorMovementProcess.retireOwned(state, movement, tick, update) : state.withChanges(update);
            return ResidentActivityProcess.retargetHotResident(retired, visit.actorId(), tick);
        }
    }

    private static boolean hasPendingOrReturnable(FrontierWorldState state, SettlementAssault assault) {
        return assault.equipmentReturnVisit().isPresent() || state.physicalIntents().values().stream().anyMatch(intent -> (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE
                        || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN)
                        && intent.lifecycleOwner() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT
                        && intent.status() != PhysicalIntentStatus.CONFIRMED && intent.roles().require(PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT).equals(assault.id()))
                || assault.defenderIds().stream().flatMap(resident -> state.inventory().actorItems(resident).stream())
                .anyMatch(item -> item.economicOwnerId().equals(assault.settlementId())
                        && HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind()));
    }
}
