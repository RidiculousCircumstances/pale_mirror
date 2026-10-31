package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Guard-owned equipment permission; common issue/custody/recovery owns the actual handoff. */
public final class GuardEquipmentStateSupport {
    public static PhysicalIntentId transferId(GuardDuty duty) {
        return new PhysicalIntentId("intent:guard-equipment-" + StableCorrelationIdentity.digest(duty.id().value()
                + "|" + duty.weapon().value() + "|" + duty.revision() + "|" + duty.execution().generation()));
    }
    public static GuardDuty require(FrontierWorldState state, PhysicalIntent intent) {
        var duty = state.strategicPlans().guards().get(intent.roles().require(PhysicalIntentSubjectRole.GUARD_DUTY));
        if (intent.lifecycleOwner() != PhysicalIntentLifecycleOwner.GUARD_DUTY || duty == null
                || !Set.of(PhysicalIntentKind.EQUIPMENT_ISSUE, PhysicalIntentKind.EQUIPMENT_RETURN).contains(intent.kind()) || !transferId(duty).equals(intent.id())
                || !duty.execution().actorId().equals(intent.roles().require(PhysicalIntentSubjectRole.GUARD_RESIDENT))
                || !duty.weapon().equals(intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT))
                || !duty.home().equals(intent.causeSubjectId()) || (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE ? duty.phase() != GuardDuty.Phase.EQUIPPING : duty.phase() != GuardDuty.Phase.RETURNING)
                || !duty.source().containerId().equals(EquipmentPhysicalAuthority.container(intent)))
            throw new IllegalArgumentException("guard equipment lost its exact appointment/source/execution generation");
        return duty;
    }
    public static void validateIssue(FrontierWorldState state, PhysicalIntent intent) {
        var duty = require(state, intent); state.actorExecutions().requireCurrent(duty.execution());
        if (!GuardAuthority.sourceAvailable(state, duty) || !duty.source().station().equals(
                state.actorLocations().get(duty.execution().actorId()).supportingSurface())
                || !ServiceAccessCoordinator.available(state, GuardAuthority.service(duty)))
            throw new IllegalArgumentException("guard equipment lacks current exact stock or service contact");
    }
    public static void validateReturn(FrontierWorldState state, PhysicalIntent intent) {
        var duty = require(state, intent); state.actorExecutions().requireCurrent(duty.execution());
        var item = state.inventory().items().get(duty.weapon());
        if (item == null || !item.custody().equals(new InventoryCustody.Actor(duty.execution().actorId()))
                || !ResourceStorageSelection.permits(state.inventory(), duty.source().containerId(), duty.home(), item.itemKind(), Set.of(ResourceStorageRole.WAREHOUSE))
                || !duty.source().station().equals(state.actorLocations().get(duty.execution().actorId()).supportingSurface())
                || !ServiceAccessCoordinator.available(state, GuardAuthority.service(duty)))
            throw new IllegalArgumentException("guard return lacks its exact holder, storage or service contact");
    }
    public static PhysicalIntent returned(FrontierWorldState state, GuardDuty duty, long tick) {
        var slot = GuardAuthority.order(state, duty).exactSlot();
        var body = state.actorLocations().get(duty.execution().actorId()).body();
        var intent = new PhysicalIntent(transferId(duty), PhysicalIntentKind.EQUIPMENT_RETURN, PhysicalIntentStatus.PREPARED,
                duty.home(), PhysicalIntentRoleBinding.guardEquipmentReturn(duty.id(), duty.execution().actorId(), duty.weapon(), duty.source().containerId()),
                new FixedPosition(FixedScalar.whole(body.x()), FixedScalar.whole(body.y()), FixedScalar.whole(body.z())),
                0, PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED, new PhysicalContainerSlot(slot.containerId(), slot.slot()),
                new EquipmentServiceRequest(GuardAuthority.service(duty), tick), PhysicalIntentLifecycleOwner.GUARD_DUTY);
        EquipmentServiceBoundary.validateIntent(state, intent); return intent;
    }
    public static void validateCommitment(FrontierWorldState state, PhysicalIntent intent) {
        var duty = require(state, intent);
        if (state.actorLocations().get(duty.execution().actorId()).condition().status() == ActorLifeStatus.ALIVE)
            state.actorExecutions().requireRetained(duty.execution());
    }
    public static Optional<SurfaceAnchor> serviceStation(FrontierWorldState state, PhysicalIntent intent) {
        return Optional.of(require(state, intent).source().station());
    }
    public static PhysicalIntent issue(FrontierWorldState state, GuardDuty duty, long tick) {
        var body = state.actorLocations().get(duty.execution().actorId()).body();
        var intent = new PhysicalIntent(transferId(duty), PhysicalIntentKind.EQUIPMENT_ISSUE, PhysicalIntentStatus.PREPARED,
                duty.home(), PhysicalIntentRoleBinding.guardEquipmentIssue(duty.id(), duty.execution().actorId(), duty.weapon(), duty.source().containerId()),
                new FixedPosition(FixedScalar.whole(body.x()), FixedScalar.whole(body.y()), FixedScalar.whole(body.z())),
                0, PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED, new EquipmentServiceRequest(GuardAuthority.service(duty), tick),
                PhysicalIntentLifecycleOwner.GUARD_DUTY);
        EquipmentServiceBoundary.validateIntent(state, intent); return intent;
    }
    private GuardEquipmentStateSupport() { }
}
