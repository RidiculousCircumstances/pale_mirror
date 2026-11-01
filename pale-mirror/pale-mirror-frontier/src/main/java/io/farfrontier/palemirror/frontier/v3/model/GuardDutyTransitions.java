package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Atomic guard-owned transitions. They cannot create a sword, resource or physical arrival. */
public final class GuardDutyTransitions {
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, GuardDutyEvent event, long tick) {
        if (event instanceof GuardDutyEvent.Appointed appointed) {
            var duty = appointed.duty(); var resident = state.humanPopulation().resident(duty.execution().actorId());
            if (!subject.equals(duty.id()) || state.strategicPlans().guards().containsKey(subject) || resident == null
                    || !resident.settlementId().equals(duty.home()) || duty.revision() != 1 || duty.phase() != GuardDuty.Phase.EQUIPPING
                    || GuardEquipment.in(state, duty.home()).stream().filter(value -> state.actorLocations()
                        .get(value.execution().actorId()).condition().status() == ActorLifeStatus.ALIVE).count() >= GuardEquipment.target(state, duty.home())
                    || !io.farfrontier.palemirror.frontier.v3.process.ResidentWorkComposition.SELECTION.admissible(
                            state, resident, ResidentWorkKind.GUARD, HumanCapability.SECURITY, tick)
                    || !WorkEquipmentSelection.available(state, duty.home(), state.inventory().items().get(duty.weapon()),
                            GuardEquipment.REQUIREMENTS, WorkEquipmentClaimComposition.reserved(state))
                    || !GuardAuthority.sourceAvailable(state, duty))
                throw new IllegalArgumentException("stale, unarmed, foreign or over-capacity guard appointment");
            return ActorExecutionComposition.LIFECYCLE.prepareVacant(state, duty.execution()).commit(state,
                    FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().withGuard(duty)));
        }
        var changed = (GuardDutyEvent.Changed) event;
        var duty = Objects.requireNonNull(state.strategicPlans().guards().get(changed.duty()), "guard appointment");
        if (!subject.equals(duty.id()) || duty.revision() != changed.revision() || GuardAuthority.pending(state, duty)
                || state.actorMovements().containsKey(duty.execution().actorId()) && changed.change() != GuardDutyEvent.Change.GOAL_WITHDRAWN)
            throw new IllegalArgumentException("guard transition has a stale or unsettled owner");
        if (changed.change() == GuardDutyEvent.Change.GOAL_WITHDRAWN) {
            var movement = state.actorMovements().get(duty.execution().actorId());
            if (movement == null || !(movement.context() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.GuardEquipment)
                    || !movement.executionId().equals(duty.execution()) || GuardAuthority.goalAvailable(state, duty))
                throw new IllegalArgumentException("guard can withdraw only its exact unavailable equipment goal");
            return io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.retireOwned(state, movement, tick, FrontierWorldStateUpdate.begin());
        }
        if (changed.change() == GuardDutyEvent.Change.ENDED) {
            if (state.actorLocations().get(duty.execution().actorId()).condition().status() == ActorLifeStatus.ALIVE
                    && (GuardEquipment.carried(state,duty) || duty.phase() != GuardDuty.Phase.RETURNING
                        && !GuardStaffingRelease.requested(state,duty)))
                throw new IllegalArgumentException("living guard cannot abandon its appointment or carried equipment");
            var executions = state.actorExecutions();
            if (executions.actors().get(duty.execution().actorId()).current().equals(Optional.of(duty.execution())))
                executions = executions.finish(duty.execution());
            return state.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().withoutGuard(duty.id()))
                    .actorExecutions(executions).security(state.security().retired(duty.id())));
        }
        state.actorExecutions().requireCurrent(duty.execution());
        if (changed.change() == GuardDutyEvent.Change.RETURN_SELECTED) {
            var selection = changed.selection().orElseThrow(); var item = state.inventory().items().get(duty.weapon());
            if (!selection.item().equals(duty.weapon()) || item == null
                    || !item.custody().equals(new InventoryCustody.Actor(duty.execution().actorId()))
                    || GuardEquipment.held(state, duty) && !GuardStaffingRelease.requested(state,duty) || !WorkEquipmentStorage.available(state, item, selection.source()))
                throw new IllegalArgumentException("guard return requires its actual unserviceable held equipment and storage");
            return state.withStrategicPlans(state.strategicPlans().withGuard(duty.returning(selection.source())));
        }
        if (changed.change() == GuardDutyEvent.Change.COLD_RETURNED) {
            var item = state.inventory().items().get(duty.weapon());
            if (duty.phase() != GuardDuty.Phase.RETURNING || item == null || !WorkEquipmentStorage.available(state, item, duty.source())
                    || !ServiceAccessCoordinator.available(state, GuardAuthority.service(duty)))
                throw new IllegalArgumentException("guard COLD return lost its storage or service permission");
            return state.withInventory(ActorItemCustody.transferCold(state, GuardAuthority.order(state, duty)));
        }
        if (changed.change() == GuardDutyEvent.Change.RESELECTED) {
            var selected = changed.selection().orElseThrow(); var next = duty.acquiring(selected.item(), selected.source());
            if (duty.phase() == GuardDuty.Phase.EQUIPPING && GuardAuthority.sourceAvailable(state, duty)
                    || !GuardAuthority.sourceAvailable(state, next))
                throw new IllegalArgumentException("guard cannot replace an available source or invent another one");
            return state.withStrategicPlans(state.strategicPlans().withGuard(next));
        }
        if (duty.phase() != GuardDuty.Phase.EQUIPPING)
            throw new IllegalArgumentException("guard equipment confirmation has a foreign phase");
        var update = FrontierWorldStateUpdate.begin();
        if (changed.change() == GuardDutyEvent.Change.COLD_ISSUED) {
            if (!GuardAuthority.sourceAvailable(state, duty) || !ServiceAccessCoordinator.available(state, GuardAuthority.service(duty)))
                throw new IllegalArgumentException("guard COLD issue lost stock or service permission");
            update.inventory(ActorItemCustody.transferCold(state, GuardAuthority.order(state, duty)));
        } else if (changed.change() != GuardDutyEvent.Change.EQUIPPED || !GuardEquipment.held(state, duty))
            throw new IllegalArgumentException("guard readiness requires an actual owned, held serviceable weapon");
        return state.withChanges(update.strategicPlans(state.strategicPlans().withGuard(duty.equipped())));
    }
    private GuardDutyTransitions() { }
}
