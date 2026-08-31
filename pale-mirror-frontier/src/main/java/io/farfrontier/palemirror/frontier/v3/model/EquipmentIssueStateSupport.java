package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Pure validation and canonical receipt for the owned defender-equipment boundary. */
public final class EquipmentIssueStateSupport {
    private EquipmentIssueStateSupport() { }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_ISSUE) throw new IllegalArgumentException("not an equipment issue intent");
        SubjectId assaultId = intent.subjectIds().get(0), residentId = intent.subjectIds().get(1), itemId = intent.subjectIds().get(2);
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(assaultId);
        ExactItemStack item = state.inventory().items().get(itemId);
        if (assault == null || assault.status() == SettlementAssaultStatus.RESOLVED || !assault.defenderIds().contains(residentId)
                || !intent.causeSubjectId().equals(assault.settlementId()) || item == null
                || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(FrontierWorldState.depotId(assault.settlementId()))
                || !item.economicOwnerId().equals(assault.settlementId())
                || !HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())
                || state.inventory().surfaces().get(slot.containerId()) == null
                || state.inventory().surfaces().get(slot.containerId()).status() != ContainerSurfaceStatus.ACTIVE
                || state.inventory().actorItems(residentId).stream().anyMatch(held -> held.itemKind().equals(item.itemKind()))) {
            throw new IllegalArgumentException("equipment issue lacks an active exact defender/depot/item precondition");
        }
    }

    /**
     * Confirmed state retains the former exact source slot in the immutable receipt, while the
     * stack itself has moved to the named actor. This is deliberately different from admission.
     */
    public static void validateReceiptForRecovery(ExactInventory inventory, PhysicalIntent intent, EquipmentIssueObservation receipt) {
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_ISSUE || !intent.subjectIds().equals(java.util.List.of(receipt.assaultId(), receipt.residentId(), receipt.itemId()))) {
            throw new IllegalArgumentException("equipment issue receipt has foreign exact subjects");
        }
        ExactItemStack item = inventory.items().get(receipt.itemId());
        if (item == null || !(item.custody() instanceof InventoryCustody.Actor actor) || !actor.actorId().equals(receipt.residentId())
                || !HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())
                || inventory.surfaces().get(receipt.sourceSlot().containerId()) == null) {
            throw new IllegalArgumentException("equipment issue receipt lacks the exact issued actor stack");
        }
    }

    static EquipmentIssueObservation requireReceipt(PhysicalEffectObservation evidence) {
        if (evidence instanceof EquipmentIssueObservation issue) return issue;
        throw new IllegalArgumentException("equipment issue requires exact hand-off evidence");
    }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, EquipmentIssueObservation receipt,
                                              java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> nextIntents) {
        validateIntent(state, intent);
        SubjectId assaultId = intent.subjectIds().get(0), residentId = intent.subjectIds().get(1), itemId = intent.subjectIds().get(2);
        ExactItemStack item = state.inventory().items().get(itemId);
        if (!receipt.assaultId().equals(assaultId) || !receipt.residentId().equals(residentId) || !receipt.itemId().equals(itemId)
                || !receipt.sourceSlot().equals(item.custody())) throw new IllegalArgumentException("equipment issue receipt does not match exact intent");
        nextIntents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new java.util.LinkedHashMap<>(state.physicalObservations());
        observations.put(receipt.id(), receipt);
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().moveObservedItem(itemId, receipt.sourceSlot(), new InventoryCustody.Actor(residentId)))
                .physicalIntents(nextIntents).physicalObservations(observations));
    }
}
