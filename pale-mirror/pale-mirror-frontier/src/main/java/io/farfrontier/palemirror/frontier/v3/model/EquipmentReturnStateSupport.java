package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;

/** Pure validation and canonical receipt for one exact human-equipment return boundary. */
public final class EquipmentReturnStateSupport {
    private EquipmentReturnStateSupport() { }

    public static InventoryCustody.ContainerSlot targetSlot(FrontierWorldState state, PhysicalIntent intent) {
        SubjectId ownerId = intent.roles().require(PhysicalIntentSubjectRole.PROJECT);
        if (state.routeConstructions().containsKey(ownerId) || state.routeMaintenances().containsKey(ownerId)) return EngineeringEquipmentStateSupport.targetSlot(state, intent);
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(ownerId);
        if (assault == null) throw new IllegalArgumentException("equipment return has no assault");
        var target = intent.targetSlot().orElseThrow(() -> new IllegalArgumentException("equipment return lacks typed target slot"));
        if (!target.containerId().equals(FrontierWorldState.depotId(assault.settlementId()))) {
            throw new IllegalArgumentException("equipment return target does not belong to its home depot");
        }
        return new InventoryCustody.ContainerSlot(target.containerId(), target.slot());
    }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_RETURN) throw new IllegalArgumentException("not an equipment return intent");
        SubjectId ownerId = intent.roles().require(PhysicalIntentSubjectRole.PROJECT), residentId = intent.roles().require(PhysicalIntentSubjectRole.DEFENDER), itemId = intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT);
        if (state.routeConstructions().containsKey(ownerId) || state.routeMaintenances().containsKey(ownerId)) {
            EngineeringEquipmentStateSupport.validateReturn(state, intent);
            return;
        }
        SubjectId assaultId = ownerId;
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(assaultId);
        ExactItemStack item = state.inventory().items().get(itemId);
        InventoryCustody.ContainerSlot target = targetSlot(state, intent);
        if (assault == null || assault.status() != SettlementAssaultStatus.RESOLVED || !assault.defenderIds().contains(residentId)
                || !intent.causeSubjectId().equals(assault.settlementId()) || item == null
                || !(item.custody() instanceof InventoryCustody.Actor actor) || !actor.actorId().equals(residentId)
                || !item.economicOwnerId().equals(assault.settlementId())
                || !HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())
                || state.actorLocations().get(residentId) == null || state.actorLocations().get(residentId).condition().status() != ActorLifeStatus.ALIVE
                || state.inventory().surfaces().get(target.containerId()) == null
                || state.inventory().surfaces().get(target.containerId()).status() != ContainerSurfaceStatus.ACTIVE
                || target.slot() >= state.inventory().containers().get(target.containerId()).slotCount()
                || state.inventory().itemAt(target.containerId(), target.slot()).isPresent()) {
            throw new IllegalArgumentException("equipment return lacks an exact released defender/active-depot target precondition");
        }
    }

    public static void validateReceiptForRecovery(ExactInventory inventory, PhysicalIntent intent, EquipmentReturnObservation receipt) {
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_RETURN || !intent.roles().equals(PhysicalIntentRoleBinding.equipmentReturn(receipt.ownerId(), receipt.residentId(), receipt.itemId()))) {
            throw new IllegalArgumentException("equipment return receipt has foreign exact subjects");
        }
    }

    static EquipmentReturnObservation requireReceipt(PhysicalEffectObservation evidence) {
        if (evidence instanceof EquipmentReturnObservation returned) return returned;
        throw new IllegalArgumentException("equipment return requires exact hand-off evidence");
    }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, EquipmentReturnObservation receipt,
                                              java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> nextIntents) {
        validateIntent(state, intent);
        SubjectId ownerId = intent.roles().require(PhysicalIntentSubjectRole.PROJECT), residentId = intent.roles().require(PhysicalIntentSubjectRole.DEFENDER), itemId = intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT);
        ExactItemStack item = state.inventory().items().get(itemId); InventoryCustody.ContainerSlot target = targetSlot(state, intent);
        if (!receipt.ownerId().equals(ownerId) || !receipt.residentId().equals(residentId) || !receipt.itemId().equals(itemId)
                || !receipt.targetSlot().equals(target) || !item.custody().equals(new InventoryCustody.Actor(residentId))) {
            throw new IllegalArgumentException("equipment return receipt does not match exact intent");
        }
        nextIntents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new java.util.LinkedHashMap<>(state.physicalObservations());
        observations.put(receipt.id(), receipt);
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().moveObservedItem(itemId, item.custody(), target))
                .physicalIntents(nextIntents).physicalObservations(observations));
    }
}
