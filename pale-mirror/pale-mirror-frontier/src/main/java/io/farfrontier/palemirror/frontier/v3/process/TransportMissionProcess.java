package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Transport arranges the workflow. Generic groups own coordinated travel; shipments own goods effects. */
public final class TransportMissionProcess {
    public static final String PROGRESS = TransportMissionContinuation.PROGRESS;
    private TransportMissionProcess() { }
    public static ScheduledAction progress(SubjectId mission, long tick) {
        return TransportMissionContinuation.at(mission, tick);
    }
    public static ProposedEvent wake(SubjectId mission, long tick) {
        return TransportMissionContinuation.wake(mission, tick);
    }
    public static boolean held(FrontierWorldState state, ScheduledAction action) {
        var mission = state.shipments().missions().get(action.subject());
        return mission != null && mission.preparingSupplies()
                && mission.replenishment().isEmpty() && mission.supplies().isPresent()
                && mission.shipmentIds().stream().anyMatch(id -> !state.shipments().shipments().get(id).terminal())
                && ExpeditionSupplyProcess.serviceHeld(state, mission);
    }
    /** The existing queue owns parking and recovery; transport supplies its exact dependency addresses. */
    public static java.util.Set<SubjectId> wakeDependencies(FrontierWorldState state, ScheduledAction action) {
        var mission = state.shipments().missions().get(action.subject());
        if (mission == null) return java.util.Set.of(action.subject());
        var keys = new java.util.HashSet<SubjectId>();
        keys.add(mission.id()); keys.add(mission.groupId()); keys.add(mission.sender().containerId()); keys.add(mission.organization().home().containerId());
        var group = state.unitGroups().groups().get(mission.groupId());
        group.members().forEach(member -> keys.add(member.actorId()));
        return java.util.Set.copyOf(keys);
    }
    public static FrontierWorldState admit(FrontierWorldState state, SubjectId subject, TransportMissionStarted value, long now) {
        var mission = value.mission(); var group = value.group();
        if (state.inventory().economics().require(value.senderId()).ownerKind() != value.senderKind())
            throw new IllegalArgumentException("transport admission has a forged sender kind");
        if (!subject.equals(mission.id()) || !group.id().equals(mission.groupId())
                || !group.mission().equals(new UnitGroup.Mission(UnitGroup.MissionKind.TRANSPORT, mission.id()))
                || !java.util.Set.copyOf(mission.shipmentIds()).equals(value.shipments().stream().map(Shipment::id).collect(java.util.stream.Collectors.toUnmodifiableSet()))
                || value.shipments().size() != mission.shipmentIds().size())
            throw new IllegalArgumentException("transport admission does not declare its exact group and shipments");
        var shipments = state.shipments();
        var declarations = new ArrayList<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId>();
        var declaredShipments = value.shipments().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Shipment::id, java.util.function.Function.identity()));
        TransportGroupMissionPort.validateDeclaration(state, mission, group, declaredShipments);
        if (now < 0 || mission.supplies().filter(load -> load.calculatedAtTick() != now).isPresent())
            throw new IllegalArgumentException("transport provisioning declaration has a stale or future calculation time");
        var supplyDeclaration = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.reserveDeclaration(state, mission, group);
        SettlementLabourAllocation.requireMissionCommitment(state, mission.organization().home().settlementId(),
                ResidentWorkKind.LOGISTICS, group.members().stream().map(UnitGroup.Member::actorId)
                    .filter(actor -> state.actorLocations().get(actor).kind() == ActorKind.RESIDENT).toList());
        TransportGroupMissionPort.validateRendezvous(state, mission.organization().home(), mission.homeRendezvous(), group.members().size());
        TransportGroupMissionPort.validateRendezvous(state, mission.organization().itinerary() == TransportOrganization.Itinerary.SOURCE_DELIVERY ? mission.receiver() : mission.sender(), mission.destinationRendezvous(), group.members().size());
        var economics = state.inventory().economics();
        var budgetAmount = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionFundingAuthority.allocation(state, mission);
        if (mission.financialBudgetId().isPresent()) {
            economics = economics.reserveBudget(new FinancialBudget(mission.financialBudgetId().orElseThrow(),
                    mission.organization().sponsor().id(), FinancialBudget.OwnerKind.TRANSPORT_MISSION, mission.id(), budgetAmount));
            if (budgetAmount.raw() == 0) throw new IllegalArgumentException("expedition declares an unfunded contingency budget");
        }
        for (var shipment : value.shipments()) {
            if (!shipment.transportMissionId().equals(Optional.of(mission.id()))) throw new IllegalArgumentException("shipment has a foreign mission binding");
            ShipmentStateSupport.validateDispatch(state, value.senderId(), shipment, mission.organization());
            shipments = shipments.admit(shipment);
            if (shipment.execution().activityKind() == io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.COURIER)
                declarations.add(shipment.execution());
        }
        // One atomic publication, including supporting purposes; no intermediate dangling references.
        for (var member : group.members()) if (member.activityKind() == io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.GROUP_MEMBER) {
            declarations.add(state.actorExecutions().next(member.actorId(), member.activityKind(), member.activityOwnerId()));
        }
        return ActorExecutionComposition.LIFECYCLE.prepareVacantGroup(state,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup(declarations)).commit(state,
                FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(supplyDeclaration).withEconomics(economics))
                        .transportFleet(mission.transportAssetId().map(actor -> state.transportFleet().reserve(actor, mission.id())).orElse(state.transportFleet()))
                        .unitGroups(state.unitGroups().admit(group)).shipments(shipments.admitMission(mission)));
    }
    public static FrontierWorldState advance(FrontierWorldState state, SubjectId subject, TransportMissionAdvanced value, long now) {
        var mission = state.shipments().missions().get(value.missionId());
        if (mission == null || !subject.equals(mission.id()) || mission.revision() != value.expectedRevision())
            throw new IllegalArgumentException("transport transition has a stale or foreign predecessor");
        if (mission.replenishment().isPresent()) throw new IllegalArgumentException("transport transition retains an unfinished stock handoff");
        if (value.next() == TransportMission.Stage.RECOVERING)
            return TransportPreparationRecovery.recall(state, mission, now);
        var group = state.unitGroups().groups().get(mission.groupId());
        var shipments = mission.shipmentIds().stream().map(id -> state.shipments().shipments().get(id)).toList();
        boolean ready = switch (value.next()) {
            case TO_SOURCE -> mission.stage() == TransportMission.Stage.PREPARING && value.departure().map(proof ->
                    io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(state, mission, now, proof)).orElse(false)
                    && ExpeditionSupplyProcess.assembled(state, mission);
            case OUTBOUND -> (mission.organization().itinerary() == TransportOrganization.Itinerary.DESTINATION_COLLECTION
                    ? mission.supplies().filter(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyLoad::complete).isPresent()
                    : value.departure().map(proof -> io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(state, mission, now, proof))
                    .orElse(mission.supplies().isEmpty() && state.bootstrap().ruleset().schemaVersion() < 17))
                    && (mission.organization().itinerary() == TransportOrganization.Itinerary.DESTINATION_COLLECTION || mission.supplies().isEmpty() || ExpeditionSupplyProcess.assembled(state, mission))
                    && shipments.stream().anyMatch(s -> s.status() == Shipment.Status.CARRYING)
                    && shipments.stream().allMatch(s -> (s.terminal() || s.status() == Shipment.Status.CARRYING)
                        && s.pendingPhysicalStep().isEmpty() && s.reception().isEmpty()
                        && (s.terminal() || ShipmentStateSupport.departureReady(state, s, mission.organization())));
            case UNLOADING -> group.phase() == UnitGroup.Phase.AT_GOAL && group.goalOrdinal() == mission.deliveryOrdinal();
            case RETURNING -> mayReturn(state, mission, group);
            case COMPLETE -> group.phase() == UnitGroup.Phase.CLOSED && (group.goalOrdinal() == 2
                    || group.members().stream().allMatch(m -> state.actorLocations().get(m.actorId()).condition().status() == ActorLifeStatus.DEAD));
            case LOADING -> mission.stage() == TransportMission.Stage.TO_SOURCE && group.phase() == UnitGroup.Phase.AT_GOAL && group.goalOrdinal() == 1;
            case PREPARING -> false;
            case RECOVERING -> throw new IllegalStateException("preparation recovery handled above");
        };
        if (!ready) throw new IllegalArgumentException("transport workflow lacks its actual loading, arrival or delivery evidence");
        var economics = state.inventory().economics();
        if (value.next() == TransportMission.Stage.COMPLETE && mission.financialBudgetId().isPresent())
            economics = economics.closeBudget(mission.financialBudgetId().orElseThrow());
        var inventory = state.inventory().withEconomics(economics);
        boolean abort = mission.preparingSupplies() && value.next() == TransportMission.Stage.RETURNING;
        if (abort) inventory = inventory.withFungibleResources(
                io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.releaseForAbort(state, mission));
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                .transportFleet(value.next() == TransportMission.Stage.COMPLETE && mission.transportAssetId().isPresent()
                    ? state.transportFleet().release(mission.transportAssetId().orElseThrow(), mission.id()) : state.transportFleet())
                .shipments(abort ? state.shipments().abortLoadingMission(mission) : state.shipments().advanceMission(mission, value.next())));
    }
    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, long currentTick) {
        try { return planReady(state, action, currentTick); }
        catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending) {
            long now = Math.max(currentTick, action.dueAt().ticks());
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(), progress(action.subject(),
                    now + state.bootstrap().ruleset().cadence().transportReviewInterval()))));
        }
    }
    private static List<ProposedEvent> planReady(FrontierWorldState state, ScheduledAction action, long currentTick) {
        var mission = state.shipments().missions().get(action.subject()); long now = Math.max(currentTick, action.dueAt().ticks());
        if (mission == null)
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        if (mission.stage() == TransportMission.Stage.COMPLETE) {
            if (canRetire(state, mission)) {
                var retirement = new ArrayList<ProposedEvent>();
                retirement.add(new ProposedEvent(mission.id(), new TransportMissionRetired(mission.id(), mission.revision())));
                retirement.add(new ProposedEvent(mission.id(), new ScheduleEffect.Cancelled(action.id())));
                retirement.add(new ProposedEvent(mission.groupId(), new ScheduleEffect.Cancelled(UnitGroupProcess.progress(mission.groupId(), now).id())));
                for (var id : mission.shipmentIds()) retirement.add(new ProposedEvent(id, new ScheduleEffect.Cancelled(ShipmentProcess.progress(id, now).id())));
                return List.copyOf(retirement);
            }
            return List.of(new ProposedEvent(mission.id(), new ScheduleEffect.Rescheduled(action.id(), progress(mission.id(),
                    now + state.bootstrap().ruleset().cadence().transportReviewInterval()))));
        }
        if (TransportPreparationRecovery.needed(state, mission, now)) {
            var recovery = new ArrayList<ProposedEvent>();
            recovery.add(new ProposedEvent(mission.id(), new TransportMissionAdvanced(mission.id(), mission.revision(),
                    TransportMission.Stage.RECOVERING, Optional.empty())));
            for (var id : mission.shipmentIds()) recovery.add(ShipmentProcess.wake(id, now));
            recovery.add(wake(mission.id(), now));
            return List.copyOf(recovery);
        }
        var group = state.unitGroups().groups().get(mission.groupId()); var events = new ArrayList<ProposedEvent>();
        if (mission.replenishment().isPresent()) {
            var transfer = mission.replenishment().orElseThrow();
            if (!io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.accessAvailable(state, mission, transfer))
                return List.of(new ProposedEvent(mission.id(), new ScheduleEffect.Rescheduled(action.id(), progress(mission.id(), now + state.bootstrap().ruleset().cadence().transportReviewInterval()))));
            if (transfer.pending().isEmpty() && !state.actorMovements().containsKey(transfer.actorId())
                    && !state.actorLocations().get(transfer.actorId()).supportingSurface().equals(transfer.station())) {
                var movement = new ActorMovement(transfer.order(mission.id(), mission.revision()).movementOrder(), now,
                        new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ExpeditionReplenishment(mission.id()), transfer.execution());
                try {
                    ActorMovementProviders.require(movement).route(state, movement, state.actorLocations().get(transfer.actorId()).supportingSurface());
                    events.add(new ProposedEvent(transfer.actorId(), new ActorMovementStarted(movement)));
                    events.add(new ProposedEvent(transfer.actorId(), new ScheduleEffect.Created(ActorMovementProcess.progress(movement, now + 1))));
                } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                    // Keep the exact instruction and expose shared navigation evidence; no invented receipt.
                }
            } else if (transfer.pending().isEmpty() && !state.actorMovements().containsKey(transfer.actorId())
                    && ActorExecutionCoordinator.coldAvailable(state, transfer.actorId())
                    && !ReferenceContainerCustody.hasLiveCustody(state, transfer.containerId())
                    && !ReferenceContainerCustody.blocksCanonicalUse(state, transfer.containerId())) {
                var received = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.cold(state, mission.id(), transfer.claimId(), now);
                events.add(new ProposedEvent(mission.id(), new ExpeditionReplenishmentColdLoaded(mission.id(), transfer.claimId())));
                events.addAll(ExpeditionSupplyProcessModule.replenishmentClearance(state, received, mission.id(), now));
                events.add(ResidentActivityProcess.wakeAfterActivity(transfer.actorId(), now));
            }
            events.add(new ProposedEvent(mission.id(), new ScheduleEffect.Rescheduled(action.id(), progress(mission.id(), now + (events.isEmpty() ? 20 : 1)))));
            return List.copyOf(events);
        }
        var refill = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.select(state, mission, now);
        if (refill.isPresent()) return List.of(new ProposedEvent(mission.id(), new ExpeditionReplenishmentStarted(mission.id(), refill.orElseThrow())),
                new ProposedEvent(mission.id(), new ScheduleEffect.Rescheduled(action.id(), progress(mission.id(), now + 1))));
        boolean allCargoTerminal = mission.shipmentIds().stream().allMatch(id -> state.shipments().shipments().get(id).terminal());
        boolean cargoLoaded = mission.shipmentIds().stream().allMatch(id -> {
            var shipment = state.shipments().shipments().get(id);
            return shipment.terminal() || shipment.status() == Shipment.Status.CARRYING;
        });
        if (mission.preparingSupplies() && !allCargoTerminal && mission.supplies().isPresent()
                && (!mission.supplies().orElseThrow().complete()
                    || ExpeditionSupplyProcess.assemblyWorkPending(state, mission)
                    || (cargoLoaded || mission.stage() == TransportMission.Stage.PREPARING) && (!ExpeditionSupplyProcess.assembled(state, mission)
                        || !io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(state, mission, now))))
            return ExpeditionSupplyProcess.plan(state, mission, action, now);
        TransportMission.Stage next = switch (mission.stage()) {
            case PREPARING -> allCargoTerminal ? (mayReturn(state, mission, group) ? TransportMission.Stage.RETURNING : null)
                    : io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(state, mission, now)
                        && ExpeditionSupplyProcess.assembled(state, mission) ? TransportMission.Stage.TO_SOURCE : null;
            case TO_SOURCE -> mayReturn(state, mission, group) ? TransportMission.Stage.RETURNING
                    : group.phase() == UnitGroup.Phase.AT_GOAL && group.goalOrdinal() == 1 ? TransportMission.Stage.LOADING : null;
            case LOADING -> allCargoTerminal
                    ? (mayReturn(state, mission, group) ? TransportMission.Stage.RETURNING : null) : mission.shipmentIds().stream().allMatch(id -> {
                var shipment = state.shipments().shipments().get(id);
                return (shipment.terminal() || shipment.status() == Shipment.Status.CARRYING) && shipment.pendingPhysicalStep().isEmpty() && shipment.reception().isEmpty()
                        && !state.actorMovements().containsKey(shipment.execution().actorId())
                        && (shipment.terminal() || ShipmentStateSupport.departureReady(state, shipment, mission.organization()));
            }) && (mission.organization().itinerary() == TransportOrganization.Itinerary.DESTINATION_COLLECTION
                    || io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(state, mission, now))
                    ? TransportMission.Stage.OUTBOUND : null;
            case OUTBOUND -> mayReturn(state, mission, group) ? TransportMission.Stage.RETURNING
                    : group.phase() == UnitGroup.Phase.AT_GOAL && group.goalOrdinal() == mission.deliveryOrdinal() ? TransportMission.Stage.UNLOADING : null;
            case UNLOADING -> mayReturn(state, mission, group)
                    ? TransportMission.Stage.RETURNING : null;
            case RECOVERING -> mayReturn(state, mission, group) ? TransportMission.Stage.RETURNING : null;
            case RETURNING -> (group.phase() == UnitGroup.Phase.AT_GOAL && group.goalOrdinal() == 2 || group.phase() == UnitGroup.Phase.CLOSED)
                    && group.members().stream().noneMatch(m -> state.humanPopulation().meals().containsKey(m.actorId()) || state.actorMovements().containsKey(m.actorId()))
                    ? TransportMission.Stage.COMPLETE : null;
            case COMPLETE -> null;
        };
        if (next == TransportMission.Stage.COMPLETE && group.phase() != UnitGroup.Phase.CLOSED) {
            events.add(new ProposedEvent(group.id(), new UnitGroupAdvanced(group.id(), group.revision(), UnitGroupAdvanced.Change.CLOSE,
                    group.goalOrdinal(), Optional.empty(), Optional.empty())));
            for (var member : group.members()) if (state.actorLocations().get(member.actorId()).kind() == ActorKind.RESIDENT)
                events.add(ResidentActivityProcess.wakeAfterActivity(member.actorId(), now));
        }
        Optional<io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionJourneyProof> departure = Optional.empty();
        if (next == TransportMission.Stage.TO_SOURCE || next == TransportMission.Stage.OUTBOUND
                && mission.organization().itinerary() == TransportOrganization.Itinerary.SOURCE_DELIVERY) {
            try { departure = Optional.of(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionJourneyProof.select(state, mission)); }
            catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending) {
                return List.of(new ProposedEvent(mission.id(), new ScheduleEffect.Rescheduled(action.id(), progress(mission.id(),
                        now + state.bootstrap().ruleset().cadence().transportReviewInterval()))));
            }
        }
        var transition = next == null ? null : new TransportMissionAdvanced(mission.id(), mission.revision(), next, departure);
        if (next != null) {
            events.add(new ProposedEvent(mission.id(), transition));
            if (next == TransportMission.Stage.UNLOADING || next == TransportMission.Stage.LOADING) for (var id : mission.shipmentIds()) events.add(ShipmentProcess.wake(id, now));
        }
        if (next == TransportMission.Stage.TO_SOURCE || next == TransportMission.Stage.OUTBOUND || next == TransportMission.Stage.RETURNING
                || next == null && (mission.stage() == TransportMission.Stage.TO_SOURCE || mission.stage() == TransportMission.Stage.OUTBOUND || mission.stage() == TransportMission.Stage.RETURNING)
                    && group.phase() != UnitGroup.Phase.TRAVELLING) {
            long ordinal = (next == TransportMission.Stage.RETURNING || mission.stage() == TransportMission.Stage.RETURNING)
                    ? mission.returnOrdinal() : (next == TransportMission.Stage.TO_SOURCE || mission.stage() == TransportMission.Stage.TO_SOURCE)
                        ? 1 : mission.deliveryOrdinal();
            var planningState = next != null ? advance(state, mission.id(), transition, now) : state;
            if (group.phase() != UnitGroup.Phase.AT_GOAL || group.goalOrdinal() != ordinal)
                UnitGroupProcess.start(planningState, group, ordinal).ifPresent(value -> {
                events.add(new ProposedEvent(group.id(), value)); events.add(UnitGroupProcess.wake(group.id(), now));
            });
        }
        events.add(new ProposedEvent(mission.id(), new ScheduleEffect.Rescheduled(action.id(), progress(mission.id(),
                now + (next != null ? 1 : state.bootstrap().ruleset().cadence().transportReviewInterval())))));
        return List.copyOf(events);
    }
    private static boolean mayReturn(FrontierWorldState state, TransportMission mission, UnitGroup group) {
        return mission.replenishment().isEmpty() && mission.shipmentIds().stream().allMatch(id -> state.shipments().shipments().get(id).terminal())
                && group.phase() != UnitGroup.Phase.TRAVELLING
                && group.members().stream().noneMatch(m -> state.actorMovements().containsKey(m.actorId()) || state.humanPopulation().meals().containsKey(m.actorId()))
                && mission.supplies().stream().flatMap(load -> load.allocations().stream()).noneMatch(a -> a.pending().isPresent());
    }
    private static boolean canRetire(FrontierWorldState state, TransportMission mission) {
        var group = state.unitGroups().groups().get(mission.groupId());
        return mission.stage() == TransportMission.Stage.COMPLETE && group != null && group.phase() == UnitGroup.Phase.CLOSED
                && mission.shipmentIds().stream().allMatch(id -> ShipmentStateSupport.cargoClosedForRetirement(state, state.shipments().shipments().get(id)))
                && state.actorMovements().values().stream().noneMatch(m -> m.order().ownerId().equals(group.id()))
                && state.actorExecutions().actors().values().stream().noneMatch(a -> java.util.stream.Stream.concat(a.current().stream(), a.suspended().stream())
                        .anyMatch(e -> e.activityOwnerId().equals(group.id())));
    }
    public static FrontierWorldState retire(FrontierWorldState state, SubjectId subject, TransportMissionRetired value) {
        var mission = state.shipments().missions().get(value.missionId());
        if (mission == null || !subject.equals(mission.id()) || mission.revision() != value.expectedRevision() || !canRetire(state, mission))
            throw new IllegalArgumentException("mission retirement lacks an exact closed group, cargo and accepted receipt");
        var economics = state.inventory().economics();
        if (mission.financialBudgetId().isPresent()) economics = economics.releaseBudget(mission.financialBudgetId().orElseThrow());
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withEconomics(economics))
                .shipments(state.shipments().retireMission(mission))
                .unitGroups(state.unitGroups().retire(state.unitGroups().groups().get(mission.groupId()))));
    }
}
