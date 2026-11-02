package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Settlement guard appointments: common staffing, navigation, custody and UAE perform their own work. */
public final class GuardWorkProcess {
    private static boolean alive(FrontierWorldState state, GuardDuty duty) {
        return state.actorLocations().get(duty.execution().actorId()).condition().status() == ActorLifeStatus.ALIVE;
    }
    public static SettlementStaffingPort.Demand staffingDemand(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules) {
        int approvedCeiling=Math.min(rules.targetWorkers(),GuardEquipment.target(state,home));
        return new SettlementStaffingPort.Demand(ResidentWorkKind.GUARD, HumanCapability.SECURITY, approvedCeiling,
                approvedCeiling, rules.priority(), GuardEquipment.in(state, home).stream().filter(duty -> alive(state, duty))
                        .map(duty -> duty.execution().actorId()).collect(java.util.stream.Collectors.toSet()));
    }
    public static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long tick) {
        return GuardEquipment.in(state, resident.settlementId()).stream().filter(duty -> alive(state, duty)).count() < GuardEquipment.target(state, resident.settlementId())
                && WorkEquipmentSelection.hasAvailable(state, resident.settlementId(), GuardEquipment.REQUIREMENTS, WorkEquipmentClaimComposition.reserved(state));
    }
    public static List<ProposedEvent> review(FrontierWorldState initial, ScheduledAction action, long tick) {
        var events = new ArrayList<ProposedEvent>(); var state = initial;
        for (var duty : GuardEquipment.in(state, action.subject())) events.add(GuardContinuation.wake(duty.id(), tick));
        var provider = new ResidentWorkProvider<GuardDuty>() {
            public ResidentWorkKind kind() { return ResidentWorkKind.GUARD; }
            public HumanCapability capability() { return HumanCapability.SECURITY; }
            public Optional<ResidentWorkOffer<GuardDuty>> discover(FrontierWorldState snapshot, ResidentProfile resident, long now) {
                if (!availableWork(snapshot, resident, now)) return Optional.empty();
                return selection(snapshot, resident.settlementId(), resident.id(), Set.of()).map(selected -> {
                    var id = new SubjectId("guard-duty:" + resident.id().value().replace(':', '-'));
                    var execution = snapshot.actorExecutions().next(resident.id(), ActorActivityKind.GUARD, id);
                    var duty = new GuardDuty(id, resident.settlementId(), execution, selected.item().id(), selected.source(), GuardDuty.Phase.EQUIPPING, 1);
                    return new ResidentWorkOffer<>(kind(), resident.id(), duty, List.of(new WorkReservationClaim.ExactResource(duty.weapon(), 1)));
                });
            }
        };
        for (var resident : ResidentWorkComposition.SELECTION.eligible(state, action.subject(), ResidentWorkKind.GUARD, HumanCapability.SECURITY, tick)) {
            if (!availableWork(state, resident, tick)) break;
            ResidentWorkSelection.Discovery<GuardDuty> offered;
            try { offered = ResidentWorkComposition.SELECTION.assess(state, resident, tick, provider); }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() == PedestrianRouteResult.Status.PLANNING) break;
                continue;
            }
            if (offered.planning()) break;
            if (offered.offer().isEmpty()) continue;
            var duty = offered.offer().orElseThrow().execution(); var appointed = new GuardDutyEvent.Appointed(duty);
            state = reduce(state, duty.id(), appointed, tick); events.add(new ProposedEvent(duty.id(), appointed));
            events.add(GuardContinuation.wake(duty.id(), tick));
        }
        events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(),
                GuardContinuation.review(action.subject(), tick + initial.bootstrap().ruleset().cadence().strategicReviewInterval()))));
        return List.copyOf(events);
    }
    private static Optional<WorkEquipmentSelection.Selection> selection(FrontierWorldState state, SubjectId home, SubjectId actor, Set<SubjectId> own) {
        var reserved = new HashSet<>(WorkEquipmentClaimComposition.reserved(state)); reserved.removeAll(own);
        return WorkEquipmentSelection.select(state, home, state.actorLocations().get(actor).supportingSurface(),
                state.actorLocations().get(actor).navigationProfile(), GuardEquipment.REQUIREMENTS, reserved);
    }
    public static List<ProposedEvent> progress(FrontierWorldState state, ScheduledAction action, long tick) {
        var duty = state.strategicPlans().guards().get(action.subject());
        if (duty == null) return cancelled(action);
        if (state.combat().pending().containsKey(duty.id())) {
            try { return GuardPatrolProcess.preparing(state,action,duty,tick); }
            catch(KnownPedestrianNavigation.RouteUnavailable waiting) { return retry(state,action,tick); }
        }
        if (GuardAuthority.equipmentPending(state, duty)) return retry(state, action, tick);
        if (!alive(state, duty)) return List.of(changed(duty, GuardDutyEvent.Change.ENDED), cancel(action));
        if (!state.actorExecutions().actors().get(duty.execution().actorId()).current().equals(Optional.of(duty.execution()))) return cancelled(action);
        var activeMove = state.actorMovements().get(duty.execution().actorId());
        if (activeMove != null) {
            if(activeMove.context() instanceof ActorMovementContext.GuardMissionLeg
                    && io.farfrontier.palemirror.frontier.v3.model.security.GuardResponse.redirect(state,duty,tick)
                    || activeMove.context() instanceof ActorMovementContext.GuardEquipment
                    && !GuardEquipment.carried(state,duty) && GuardStaffingRelease.requested(state,duty)) {
                var assessment=new ActorMovementInterruptionPlanner().assessForOwnerTransition(state,duty.execution(),tick);
                if(assessment instanceof ActivityInterruptionPlanner.Waiting)return retry(state,action,tick);
                var ready=((ActivityInterruptionPlanner.Ready)assessment).validate(state,duty.execution().actorId());
                var events=new ArrayList<>(ready.events());
                events.add(cancel(action));events.add(GuardContinuation.wake(duty.id(),tick));
                return List.copyOf(events);
            }
            if (activeMove.context() instanceof ActorMovementContext.GuardEquipment && !GuardAuthority.goalAvailable(state, duty))
                return List.of(changed(duty, GuardDutyEvent.Change.GOAL_WITHDRAWN),
                        new ProposedEvent(duty.execution().actorId(), new ScheduleEffect.Cancelled(
                                ActorMovementProcess.progress(activeMove, activeMove.issuedAtTick() + 1).id())),
                        cancel(action), GuardContinuation.wake(duty.id(), tick));
            if(activeMove.context() instanceof ActorMovementContext.GuardMissionLeg) {
                try { return GuardPatrolProcess.moving(state,action,duty,activeMove,tick); }
                catch(KnownPedestrianNavigation.RouteUnavailable waiting) { return retry(state,action,tick); }
            }
            return cancelled(action);
        }
        var item = state.inventory().items().get(duty.weapon());
        boolean carried = GuardEquipment.carried(state,duty);
        if (!carried && duty.phase()!=GuardDuty.Phase.RETURNING && GuardStaffingRelease.requested(state,duty))
            return List.of(changed(duty,GuardDutyEvent.Change.ENDED),cancel(action),
                    ResidentActivityProcess.wakeAfterActivity(duty.execution().actorId(),tick));
        if (duty.phase() == GuardDuty.Phase.RETURNING && !carried) {
            var events = new ArrayList<ProposedEvent>();
            var clearance = clearance(state, duty, tick);
            if (clearance.isPresent()) {
                events.addAll(ActorMovementProcess.planStart(clearance.orElseThrow()));
                events.addAll(retry(state, action, tick));
            } else {
                events.add(changed(duty, GuardDutyEvent.Change.ENDED)); events.add(cancel(action));
                events.add(ResidentActivityProcess.wakeAfterActivity(duty.execution().actorId(), tick));
            }
            return List.copyOf(events);
        }
        if (duty.phase() != GuardDuty.Phase.RETURNING && GuardEquipment.held(state, duty) && !GuardStaffingRelease.requested(state,duty)) {
            if (duty.phase() == GuardDuty.Phase.ON_DUTY) {
                try { return GuardPatrolProcess.progress(state,action,duty,tick); }
                catch(KnownPedestrianNavigation.RouteUnavailable waiting) { return retry(state,action,tick); }
            }
            return completedIssue(state, action, duty, tick, GuardDutyEvent.Change.EQUIPPED);
        }
        if (carried && (duty.phase() != GuardDuty.Phase.RETURNING || !WorkEquipmentStorage.available(state, item, duty.source()))) {
            var destination = WorkEquipmentStorage.select(state, item, state.actorLocations().get(duty.execution().actorId()).supportingSurface(),
                    state.actorLocations().get(duty.execution().actorId()).navigationProfile());
            if (destination.isEmpty()) return retry(state, action, tick);
            return List.of(new ProposedEvent(duty.id(), new GuardDutyEvent.Changed(duty.id(), duty.revision(), GuardDutyEvent.Change.RETURN_SELECTED,
                    Optional.of(new GuardDutyEvent.Selection(duty.weapon(), destination.orElseThrow())))), GuardContinuation.wake(duty.id(), tick));
        }
        if (!carried && (duty.phase() != GuardDuty.Phase.EQUIPPING || !GuardAuthority.sourceAvailable(state, duty))) {
            Optional<WorkEquipmentSelection.Selection> selected;
            try { selected = selection(state, duty.home(), duty.execution().actorId(), Set.of(duty.weapon())); }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return retry(state, action, tick); }
            if (selected.isEmpty()) return retry(state, action, tick);
            var choice = selected.orElseThrow();
            return List.of(new ProposedEvent(duty.id(), new GuardDutyEvent.Changed(duty.id(), duty.revision(), GuardDutyEvent.Change.RESELECTED,
                    Optional.of(new GuardDutyEvent.Selection(choice.item().id(), choice.source())))), GuardContinuation.wake(duty.id(), tick));
        }
        if (!duty.order().arrivedAt(state.actorLocations().get(duty.execution().actorId()).supportingSurface())) {
            var events = new ArrayList<>(ActorMovementProcess.planStart(new ActorMovement(duty.order(), tick,
                    new ActorMovementContext.GuardEquipment(duty.id(), duty.revision()), duty.execution())));
            events.add(cancel(action)); return List.copyOf(events);
        }
        if (!ServiceAccessCoordinator.available(state, GuardAuthority.service(duty))) return retry(state, action, tick);
        if (ActorExecutionCoordinator.coldAvailable(state, duty.execution().actorId())) {
            // A live container alone cannot supply a native actor hand. Await its common release
            // (or actual actor admission), rather than pinning an actorless physical effect.
            if (ReferenceContainerCustody.hasLiveCustody(state, duty.source().containerId())) return retry(state, action, tick);
            if (duty.phase() == GuardDuty.Phase.EQUIPPING)
                return completedIssue(state, action, duty, tick, GuardDutyEvent.Change.COLD_ISSUED);
            return List.of(changed(duty, GuardDutyEvent.Change.COLD_RETURNED), cancel(action), GuardContinuation.wake(duty.id(), tick));
        }
        var intent = duty.phase() == GuardDuty.Phase.EQUIPPING ? GuardEquipmentStateSupport.issue(state, duty, tick)
                : GuardEquipmentStateSupport.returned(state, duty, tick);
        return List.of(new ProposedEvent(duty.home(), new PhysicalIntentPrepared(intent)), cancel(action));
    }
    private static List<ProposedEvent> completedIssue(FrontierWorldState state, ScheduledAction action, GuardDuty duty, long tick, GuardDutyEvent.Change change) {
        var event = changed(duty, change);
        var after = reduce(state, duty.id(), (GuardDutyEvent) event.payload(), tick);
        var events = new ArrayList<ProposedEvent>(); events.add(event);
        clearance(after, duty, tick)
                .ifPresent(move -> events.addAll(ActorMovementProcess.planStart(move)));
        events.add(cancel(action)); events.add(GuardContinuation.wake(duty.id(), tick)); return List.copyOf(events);
    }

    private static Optional<ActorMovement> clearance(FrontierWorldState state, GuardDuty duty, long tick) {
        return ServiceBoundaryComposition.operational(state, duty.source().containerId()).isEmpty() ? Optional.empty()
                : ResourceAccessClearance.select(state, duty.source().containerId(), duty.execution(), tick);
    }
    private static ProposedEvent changed(GuardDuty duty, GuardDutyEvent.Change change) {
        return new ProposedEvent(duty.id(), new GuardDutyEvent.Changed(duty.id(), duty.revision(), change, Optional.empty()));
    }
    private static ProposedEvent cancel(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())); }
    private static List<ProposedEvent> cancelled(ScheduledAction action) { return List.of(cancel(action)); }
    private static List<ProposedEvent> retry(FrontierWorldState state, ScheduledAction action, long tick) {
        return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Rescheduled(action.id(), GuardContinuation.progress(action.subject(),
                tick + state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
    }
    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, GuardDutyEvent event, long tick) {
        return GuardDutyTransitions.apply(state, subject, event, tick);
    }
    private GuardWorkProcess() { }
}
