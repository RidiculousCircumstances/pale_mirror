package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;

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
        return switch (intent.roles().schema()) {
            case HIVE_GROWTH_CONSUMPTION -> hiveGrowthClaim(state, intent);
            case POPULATION_BIRTH_CONSUMPTION -> populationBirthClaim(state, intent);
            case MEDICAL_TREATMENT_CONSUMPTION -> medicalTreatmentClaim(state, intent);
            case SETTLEMENT_PROVISION_CONSUMPTION -> settlementProvisionClaim(state, intent);
            default -> throw new IllegalArgumentException("exact consumption has foreign role schema");
        };
    }

    private static Claim hiveGrowthClaim(FrontierWorldState state, PhysicalIntent intent) {
        HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.roles().require(PhysicalIntentSubjectRole.HIVE_GROWTH_JOB));
        if (job == null || !job.consumptionIntentId().equals(intent.id()) || !intent.causeSubjectId().equals(job.id())) throw new IllegalArgumentException("exact consumption does not bind its hive growth job");
        return ownedActiveClaim(state, job.consumedItemId(), state::isHiveStore, "hive store", 64);
    }

    private static Claim populationBirthClaim(FrontierWorldState state, PhysicalIntent intent) {
        ResidentBirthJob birth = state.humanPopulation().birthJobs().get(intent.roles().require(PhysicalIntentSubjectRole.POPULATION_BIRTH_JOB));
        if (birth == null || !birth.consumptionIntentId().equals(intent.id()) || !intent.causeSubjectId().equals(birth.id())) throw new IllegalArgumentException("exact consumption does not bind its resident birth permit");
        Claim claim = ownedActiveClaim(state, birth.foodItemId(), FrontierWorldState.depotId(birth.settlementId())::equals, "settlement depot", 1);
        if (!claim.ownerId().equals(birth.settlementId()) || !claim.item().itemKind().equals("minecraft:bread")) throw new IllegalArgumentException("resident birth has no exact owned food stack");
        return claim;
    }

    private static Claim medicalTreatmentClaim(FrontierWorldState state, PhysicalIntent intent) {
        MedicalEvacuationOperation medical = state.humanPopulation().medicalOperations().get(intent.roles().require(PhysicalIntentSubjectRole.MEDICAL_TREATMENT_OPERATION));
        if (medical == null || !medical.consumptionIntentId().equals(intent.id()) || !intent.causeSubjectId().equals(medical.id())) throw new IllegalArgumentException("exact consumption does not bind its medical treatment");
        Claim claim = ownedActiveClaim(state, medical.supplyItemId(), FrontierWorldState.depotId(medical.settlementId())::equals, "settlement depot", 1);
        if (!claim.ownerId().equals(medical.settlementId()) || !MedicalEvacuationStateSupport.FIRST_TREATMENT_SUPPLY.equals(claim.item().itemKind())) throw new IllegalArgumentException("medical treatment has no exact local supply");
        return claim;
    }

    private static Claim settlementProvisionClaim(FrontierWorldState state, PhysicalIntent intent) {
        SubjectId settlementId = intent.roles().require(PhysicalIntentSubjectRole.SETTLEMENT_PROVISION);
        SettlementProvision provision = state.humanPopulation().provisions().get(settlementId);
        if (provision == null || !intent.causeSubjectId().equals(settlementId) || provision.activeIntentId().filter(intent.id()::equals).isEmpty()) throw new IllegalArgumentException("exact consumption does not bind its settlement provision allocation");
        SettlementRationAllocation allocation = provision.currentOrActiveAllocation();
        Claim claim = ownedActiveClaim(state, allocation.itemId(), FrontierWorldState.depotId(settlementId)::equals, "settlement depot", allocation.count());
        if (!claim.ownerId().equals(settlementId) || !claim.item().itemKind().equals("minecraft:bread")) throw new IllegalArgumentException("settlement provision has no exact owned food stack");
        return claim;
    }

    static void validateReceipt(PhysicalIntent intent, ExactItemConsumedObservation receipt) {
        if (intent.kind() != PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) throw new IllegalArgumentException("not an exact consumption receipt");
        if (!intent.roles().require(PhysicalIntentSubjectRole.ITEM).equals(receipt.itemId())) throw new IllegalArgumentException("exact consumption receipt names a foreign stack");
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
