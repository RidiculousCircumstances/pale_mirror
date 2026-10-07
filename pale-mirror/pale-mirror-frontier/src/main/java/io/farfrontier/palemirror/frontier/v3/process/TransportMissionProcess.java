package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
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
        SettlementLabourAllocation.requireMissionCommitment(state, mission.sender().settlementId(),
                ResidentWorkKind.LOGISTICS, group.members().stream().map(UnitGroup.Member::actorId).toList());
        TransportGroupMissionPort.validateRendezvous(state, mission.sender(), mission.homeRendezvous(), group.members().size());
        TransportGroupMissionPort.validateRendezvous(state, mission.receiver(), mission.destinationRendezvous(), group.members().size());
        var economics = state.inventory().economics();
        var budgetAmount = state.bootstrap().ruleset().expedition().replenishmentBudget();
        if (mission.financialBudgetId().isPresent()) {
            economics = economics.reserveBudget(new FinancialBudget(mission.financialBudgetId().orElseThrow(),
                    mission.sender().settlementId(), FinancialBudget.OwnerKind.TRANSPORT_MISSION, mission.id(), budgetAmount));
        } else if (budgetAmount.raw() > 0 && state.bootstrap().ruleset().schemaVersion() >= 17)
            throw new IllegalArgumentException("current expedition admission omits its declared replenishment budget");
        for (var shipment : value.shipments()) {
            if (!shipment.transportMissionId().equals(Optional.of(mission.id()))) throw new IllegalArgumentException("shipment has a foreign mission binding");
            ShipmentStateSupport.validateDispatch(state, value.senderId(), shipment);
            shipments = shipments.admit(shipment);
            declarations.add(shipment.execution());
        }
        // One atomic publication, including supporting purposes; no intermediate dangling references.
        for (var member : group.members()) if (member.activityKind() == io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.GROUP_MEMBER) {
            declarations.add(state.actorExecutions().next(member.actorId(), member.activityKind(), member.activityOwnerId()));
        }
        return ActorExecutionComposition.LIFECYCLE.prepareVacantGroup(state,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup(declarations)).commit(state,
                FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(supplyDeclaration).withEconomics(economics))
                        .unitGroups(state.unitGroups().admit(group)).shipments(shipments.admitMission(mission)));
    }
    public static FrontierWorldState advance(FrontierWorldState state, SubjectId subject, TransportMissionAdvanced value, long now) {
        var mission = state.shipments().missions().get(value.missionId());
        if (mission == null || !subject.equals(mission.id()) || mission.revision() != value.expectedRevision())
            throw new IllegalArgumentException("transport transition has a stale or foreign predecessor");
        var group = state.unitGroups().groups().get(mission.groupId());
        var shipments = mission.shipmentIds().stream().map(id -> state.shipments().shipments().get(id)).toList();
        boolean ready = switch (value.next()) {
            case OUTBOUND -> io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(state, mission, now)
                    && (mission.supplies().isEmpty() || ExpeditionSupplyProcess.assembled(state, mission))
                    && shipments.stream().allMatch(s -> s.status() == Shipment.Status.CARRYING && s.pendingPhysicalStep().isEmpty() && s.reception().isEmpty());
            case UNLOADING -> group.phase() == UnitGroup.Phase.AT_GOAL && group.goalOrdinal() == 1;
            case RETURNING -> shipments.stream().allMatch(Shipment::terminal)
                    && group.phase() != UnitGroup.Phase.TRAVELLING
                    && group.members().stream().noneMatch(m -> state.actorMovements().containsKey(m.actorId()) || state.humanPopulation().meals().containsKey(m.actorId()))
                    && mission.supplies().stream().flatMap(load -> load.allocations().stream()).noneMatch(a -> a.pending().isPresent());
            case COMPLETE -> group.phase() == UnitGroup.Phase.CLOSED && group.goalOrdinal() == 2;
            case LOADING -> false;
        };
        if (!ready) throw new IllegalArgumentException("transport workflow lacks its actual loading, arrival or delivery evidence");
        var economics = state.inventory().economics();
        if (value.next() == TransportMission.Stage.COMPLETE && mission.financialBudgetId().isPresent())
            economics = economics.closeBudget(mission.financialBudgetId().orElseThrow());
        var inventory = state.inventory().withEconomics(economics);
        boolean abort = mission.stage() == TransportMission.Stage.LOADING && value.next() == TransportMission.Stage.RETURNING;
        if (abort) inventory = inventory.withFungibleResources(
                io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.releaseForAbort(state, mission));
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                .shipments(abort ? state.shipments().abortLoadingMission(mission) : state.shipments().advanceMission(mission, value.next())));
    }
    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, long currentTick) {
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
        var group = state.unitGroups().groups().get(mission.groupId()); var events = new ArrayList<ProposedEvent>();
        boolean allCargoTerminal = mission.shipmentIds().stream().allMatch(id -> state.shipments().shipments().get(id).terminal());
        if (mission.stage() == TransportMission.Stage.LOADING && !allCargoTerminal && mission.supplies().isPresent()
                && (!ExpeditionSupplyProcess.assembled(state, mission)
                    || !io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(state, mission, now)))
            return ExpeditionSupplyProcess.plan(state, mission, action, now);
        TransportMission.Stage next = switch (mission.stage()) {
            case LOADING -> allCargoTerminal
                    ? (group.members().stream().noneMatch(m -> state.actorMovements().containsKey(m.actorId()) || state.humanPopulation().meals().containsKey(m.actorId()))
                        && mission.supplies().stream().flatMap(load -> load.allocations().stream()).noneMatch(a -> a.pending().isPresent())
                            ? TransportMission.Stage.RETURNING : null) : mission.shipmentIds().stream().allMatch(id -> {
                var shipment = state.shipments().shipments().get(id);
                return shipment.status() == Shipment.Status.CARRYING && shipment.pendingPhysicalStep().isEmpty() && shipment.reception().isEmpty()
                        && !state.actorMovements().containsKey(shipment.execution().actorId());
            }) && io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(state, mission, now)
                    ? TransportMission.Stage.OUTBOUND : null;
            case OUTBOUND -> group.phase() == UnitGroup.Phase.AT_GOAL && group.goalOrdinal() == 1
                    ? (mission.shipmentIds().stream().allMatch(id -> state.shipments().shipments().get(id).terminal())
                        ? TransportMission.Stage.RETURNING : TransportMission.Stage.UNLOADING) : null;
            case UNLOADING -> mission.shipmentIds().stream().allMatch(id -> state.shipments().shipments().get(id).terminal())
                    ? TransportMission.Stage.RETURNING : null;
            case RETURNING -> group.phase() == UnitGroup.Phase.AT_GOAL && group.goalOrdinal() == 2
                    && group.members().stream().noneMatch(m -> state.humanPopulation().meals().containsKey(m.actorId()) || state.actorMovements().containsKey(m.actorId()))
                    ? TransportMission.Stage.COMPLETE : null;
            case COMPLETE -> null;
        };
        if (next == TransportMission.Stage.COMPLETE) {
            events.add(new ProposedEvent(group.id(), new UnitGroupAdvanced(group.id(), group.revision(), UnitGroupAdvanced.Change.CLOSE,
                    group.goalOrdinal(), Optional.empty(), Optional.empty())));
            for (var member : group.members()) events.add(ResidentActivityProcess.wakeAfterActivity(member.actorId(), now));
        }
        if (next != null) {
            var transition = new TransportMissionAdvanced(mission.id(), mission.revision(), next);
            events.add(new ProposedEvent(mission.id(), transition));
            if (next == TransportMission.Stage.UNLOADING) for (var id : mission.shipmentIds()) events.add(ShipmentProcess.wake(id, now));
        }
        if (next == TransportMission.Stage.OUTBOUND || next == TransportMission.Stage.RETURNING
                || next == null && (mission.stage() == TransportMission.Stage.OUTBOUND || mission.stage() == TransportMission.Stage.RETURNING)
                    && group.phase() != UnitGroup.Phase.TRAVELLING) {
            long ordinal = (next == TransportMission.Stage.RETURNING || mission.stage() == TransportMission.Stage.RETURNING) ? 2 : 1;
            var planningState = next != null ? advance(state, mission.id(), new TransportMissionAdvanced(mission.id(), mission.revision(), next), now) : state;
            UnitGroupProcess.start(planningState, group, ordinal).ifPresent(value -> {
                events.add(new ProposedEvent(group.id(), value)); events.add(UnitGroupProcess.wake(group.id(), now));
            });
        }
        events.add(new ProposedEvent(mission.id(), new ScheduleEffect.Rescheduled(action.id(), progress(mission.id(),
                now + state.bootstrap().ruleset().cadence().transportReviewInterval()))));
        return List.copyOf(events);
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
