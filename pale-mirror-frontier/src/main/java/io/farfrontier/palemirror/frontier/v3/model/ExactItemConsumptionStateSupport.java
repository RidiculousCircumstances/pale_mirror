package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;

/** Pure ownership and receipt contract for an irreversible exact-stack consumption. */
public final class ExactItemConsumptionStateSupport {
    private ExactItemConsumptionStateSupport() { }

    /** Resolves the one active exact stack which a supported process is permitted to consume. */
    public static Claim claim(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) throw new IllegalArgumentException("not an exact consumption intent");
        HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.causeSubjectId());
        if (job != null) {
            if (!job.consumptionIntentId().equals(intent.id()) || !intent.subjectIds().equals(List.of(job.id(), job.consumedItemId()))) {
                throw new IllegalArgumentException("exact consumption does not bind its hive growth job");
            }
            return ownedActiveClaim(state, job.consumedItemId(), state::isHiveStore, "hive store", 64);
        }
        ResidentBirthJob birth = state.humanPopulation().birthJobs().get(intent.causeSubjectId());
        if (birth != null) {
            if (!birth.consumptionIntentId().equals(intent.id()) || !intent.subjectIds().equals(List.of(birth.id(), birth.foodItemId()))) {
                throw new IllegalArgumentException("exact consumption does not bind its resident birth permit");
            }
            SubjectId depot = FrontierWorldState.depotId(birth.settlementId());
            Claim claim = ownedActiveClaim(state, birth.foodItemId(), depot::equals, "settlement depot", 1);
            if (!claim.ownerId().equals(birth.settlementId()) || !claim.item().itemKind().equals("minecraft:bread")) {
                throw new IllegalArgumentException("resident birth has no exact owned food stack");
            }
            return claim;
        }
        MedicalEvacuationOperation medical = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
        if (medical != null) {
            if (!medical.consumptionIntentId().equals(intent.id()) || !intent.subjectIds().equals(List.of(medical.id(), medical.supplyItemId()))) {
                throw new IllegalArgumentException("exact consumption does not bind its medical treatment");
            }
            Claim claim = ownedActiveClaim(state, medical.supplyItemId(), FrontierWorldState.depotId(medical.settlementId())::equals, "settlement depot", 1);
            if (!claim.ownerId().equals(medical.settlementId()) || !MedicalEvacuationStateSupport.FIRST_TREATMENT_SUPPLY.equals(claim.item().itemKind())) {
                throw new IllegalArgumentException("medical treatment has no exact local supply");
            }
            return claim;
        }
        for (SettlementProvision provision : state.humanPopulation().provisions().values()) {
            if (provision.activeIntentId().filter(intent.id()::equals).isEmpty()) continue;
            SettlementRationAllocation allocation = provision.currentOrActiveAllocation();
            if (!intent.causeSubjectId().equals(provision.settlementId()) || !intent.subjectIds().equals(List.of(provision.settlementId(), allocation.itemId()))) {
                throw new IllegalArgumentException("exact consumption does not bind its settlement provision allocation");
            }
            SubjectId depot = FrontierWorldState.depotId(provision.settlementId());
            Claim claim = ownedActiveClaim(state, allocation.itemId(), depot::equals, "settlement depot", allocation.count());
            if (!claim.ownerId().equals(provision.settlementId()) || !claim.item().itemKind().equals("minecraft:bread")) {
                throw new IllegalArgumentException("settlement provision has no exact owned food stack");
            }
            return claim;
        }
        throw new IllegalArgumentException("exact consumption has no supported owning process");
    }

    static void validateReceipt(PhysicalIntent intent, ExactItemConsumedObservation receipt) {
        if (intent.kind() != PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) throw new IllegalArgumentException("not an exact consumption receipt");
        if (!intent.subjectIds().contains(receipt.itemId())) throw new IllegalArgumentException("exact consumption receipt names a foreign stack");
    }

    private static Claim ownedActiveClaim(FrontierWorldState state, SubjectId itemId, java.util.function.Predicate<SubjectId> allowedContainer, String label, int count) {
        ExactItemStack item = state.inventory().items().get(itemId);
        if (item == null || item.count() < count || !(item.custody() instanceof InventoryCustody.ContainerSlot slot) || !allowedContainer.test(slot.containerId())) {
            throw new IllegalArgumentException("exact consumption has no active " + label + " stack");
        }
        ContainerRecord container = state.inventory().containers().get(slot.containerId());
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        if (container == null || surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) {
            throw new IllegalArgumentException("exact consumption container is not active");
        }
        return new Claim(item, container.ownerId(), slot.containerId(), slot.slot(), surface.position(), count);
    }

    /** Immutable exact physical target; it carries no Minecraft type. */
    public record Claim(ExactItemStack item, SubjectId ownerId, SubjectId containerId, int slot, BlockPosition position, int count) { }
}
