package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Pure ownership and receipt contract for an irreversible exact-stack consumption. */
final class ExactItemConsumptionStateSupport {
    private ExactItemConsumptionStateSupport() { }

    static SubjectId owner(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) throw new IllegalArgumentException("not an exact consumption intent");
        HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.causeSubjectId());
        if (job == null || !job.consumptionIntentId().equals(intent.id())) throw new IllegalArgumentException("exact consumption has no owning growth job");
        ExactItemStack item = state.inventory().items().get(job.consumedItemId());
        if (item == null || !(item.custody() instanceof InventoryCustody.ContainerSlot slot) || !state.isHiveStore(slot.containerId())) {
            throw new IllegalArgumentException("exact consumption has no active hive-store stack");
        }
        ContainerRecord container = state.inventory().containers().get(slot.containerId());
        if (container == null) throw new IllegalArgumentException("exact consumption hive store is missing its container");
        return container.ownerId();
    }

    static void validateReceipt(HiveColony colony, PhysicalIntent intent, ExactItemConsumedObservation receipt) {
        if (intent.kind() != PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) throw new IllegalArgumentException("not an exact consumption receipt");
        if (!intent.subjectIds().contains(receipt.itemId())) throw new IllegalArgumentException("exact consumption receipt names a foreign stack");
    }
}
