package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Exact guard relations. Current execution and physical custody remain with their common owners. */
public final class GuardAuthority {
    private static final ImmutableInvariantCheck<Void> REFERENCES = new ImmutableInvariantCheck<>("guard-references");
    public static GuardDuty require(FrontierWorldState state, ActorExecutionId execution) {
        var duty = state.strategicPlans().guards().get(execution.activityOwnerId());
        if (execution.activityKind() != ActorActivityKind.GUARD || duty == null || !duty.execution().equals(execution))
            throw new IllegalArgumentException("guard execution lost its exact appointment");
        return duty;
    }
    public static void validateReferences(FrontierBootstrap bootstrap, StrategicPlanState plans,
            HumanPopulation people, Map<SubjectId, ActorLocation> actors, ActorExecutionState executions,
            io.farfrontier.palemirror.frontier.v3.model.combat.CombatState combat) {
        REFERENCES.validate(() -> {validate(bootstrap, plans, people, actors, executions);validateCombat(plans,combat);}, bootstrap, plans.guards(), people.residents(),
                executions, ActorIdentityDeclarations.lifeStatuses(bootstrap, actors),combat.pending(),combat.maneuvers());
    }
    private static void validate(FrontierBootstrap bootstrap, StrategicPlanState plans, HumanPopulation people,
            Map<SubjectId, ActorLocation> actors, ActorExecutionState executions) {
        var homes = new HashMap<SubjectId, Integer>(); var weapons = new HashSet<SubjectId>();
        for (var duty : plans.guards().values()) {
            var resident = people.resident(duty.execution().actorId());
            var entry = bootstrap.ruleset().labour().entries().get(ResidentWorkKind.GUARD);
            var actor = actors.get(duty.execution().actorId());
            if (entry == null || resident == null || actor == null || !resident.settlementId().equals(duty.home())
                    || duty.execution().generation() > executions.generation(resident.id()))
                throw new IllegalArgumentException("guard appointment lost its declared living-world relationship");
            if (actor.condition().status() == ActorLifeStatus.ALIVE && homes.merge(duty.home(), 1, Integer::sum) > entry.targetWorkers()
                    || !weapons.add(duty.weapon()))
                throw new IllegalArgumentException("guard appointments exceed living staffing or share exact equipment");
            if (actors.get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                executions.requireRetained(duty.execution());
        }
        for (var execution : java.util.stream.Stream.concat(executions.current(ActorActivityKind.GUARD).values().stream(),
                executions.suspended().stream().filter(value -> value.activityKind() == ActorActivityKind.GUARD)).toList()) {
            var duty = plans.guards().get(execution.activityOwnerId());
            if (duty == null || !duty.execution().equals(execution)) throw new IllegalArgumentException("ownerless guard execution");
        }
    }

    private static void validateCombat(StrategicPlanState plans,io.farfrontier.palemirror.frontier.v3.model.combat.CombatState combat){
        for(var m:combat.maneuvers().values())if(m.actor().execution().activityKind()==ActorActivityKind.GUARD){
            var duty=plans.guards().get(m.owner());
            if(duty==null || !m.retainedBy(duty.execution(),duty.weapon()))throw new IllegalArgumentException("maneuver lost exact guard permission owner");
        }
        for(var strike:combat.pending().values())if(strike.attacker().execution().activityKind()==ActorActivityKind.GUARD)requirePendingCombat(plans,strike);
    }
    public static void requirePendingCombat(StrategicPlanState plans,io.farfrontier.palemirror.frontier.v3.model.combat.MeleeStrike strike){
        var duty=plans.guards().get(strike.owner());
        if(duty==null || !duty.execution().equals(strike.attacker().execution()) || duty.revision()!=strike.ownerRevision() || !duty.weapon().equals(strike.weapon()))
            throw new IllegalArgumentException("pending melee lost its exact guard execution/weapon/revision");
    }
    public static ServiceAccessDemand.Identity service(GuardDuty duty) {
        return new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.GUARD_EQUIPMENT, duty.id(), duty.execution().actorId(), duty.source().containerId());
    }
    public static boolean pending(FrontierWorldState state, GuardDuty duty) {
        return pendingEffect(state,duty) || state.combat().maneuverOpen(duty.id());
    }
    public static boolean pendingEffect(FrontierWorldState state,GuardDuty duty){return state.combat().pending().containsKey(duty.id()) || equipmentPending(state,duty);}
    public static boolean equipmentPending(FrontierWorldState state,GuardDuty duty) {
        return state.physicalIntents().values().stream().anyMatch(intent -> intent.lifecycleOwner() == PhysicalIntentLifecycleOwner.GUARD_DUTY
                && intent.roles().require(PhysicalIntentSubjectRole.GUARD_DUTY).equals(duty.id())
                && intent.status() != PhysicalIntentStatus.CONFIRMED);
    }
    public static ActorContainerItemOrder order(FrontierWorldState state, GuardDuty duty) {
        return WorkEquipmentOrders.create(state, duty.id(), duty.execution().actorId(),
                Objects.requireNonNull(state.inventory().items().get(duty.weapon())), duty.source(),
                duty.phase() == GuardDuty.Phase.RETURNING ? ActorContainerItemOrder.Direction.PLACE : ActorContainerItemOrder.Direction.TAKE, Optional.empty(), duty.phase().wireTag(), duty.revision());
    }
    public static boolean goalAvailable(FrontierWorldState state, GuardDuty duty) {
        if (duty.phase() == GuardDuty.Phase.EQUIPPING) return sourceAvailable(state, duty);
        var item = state.inventory().items().get(duty.weapon());
        return duty.phase() == GuardDuty.Phase.RETURNING && item != null && WorkEquipmentStorage.available(state, item, duty.source());
    }
    public static boolean sourceAvailable(FrontierWorldState state, GuardDuty duty) {
        var unavailable = new HashSet<>(WorkEquipmentClaimComposition.reserved(state)); unavailable.remove(duty.weapon());
        var item = state.inventory().items().get(duty.weapon());
        return WorkEquipmentSelection.available(state, duty.home(), item, GuardEquipment.REQUIREMENTS, unavailable)
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(duty.source().containerId())
                && state.inventory().containers().get(slot.containerId()).storage().orElseThrow().endpoint().withStation(duty.source().station()).equals(duty.source())
                && ResourceStorageAccess.at(state, slot.containerId()).stations().contains(duty.source().station());
    }
    private GuardAuthority() { }
}
