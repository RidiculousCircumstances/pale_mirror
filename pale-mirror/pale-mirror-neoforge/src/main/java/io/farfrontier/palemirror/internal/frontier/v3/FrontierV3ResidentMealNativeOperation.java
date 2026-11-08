package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Set;

/** Food-owner declarations shared by normal execution and retired-resource recovery. */
final class FrontierV3ResidentMealNativeOperation {
    private FrontierV3ResidentMealNativeOperation() { }
    static String key(ResidentMeal meal, ResidentMealPhysicalStep step) {
        return key(meal.residentId(), step);
    }
    static String key(io.farfrontier.palemirror.frontier.v3.api.SubjectId resident, ResidentMealPhysicalStep step) {
        // The resource step has its own complete identity. Joint pose participants do not manufacture a second food operation.
        var key = new net.minecraft.nbt.CompoundTag();
        key.putString("actor", resident.value()); key.putInt("phase", FrontierWireTags.tag(step.phase()));
        key.putString("executionActor", step.executionId().actorId().value());
        key.putInt("activityKind", FrontierWireTags.tag(step.executionId().activityKind()));
        key.putString("activityOwner", step.executionId().activityOwnerId().value()); key.putLong("generation", step.executionId().generation());
        key.putLong("sourceEpoch", step.sourceEpoch()); key.putLong("destinationEpoch", step.destinationEpoch());
        key.putLong("ambientRevision", step.ambientRevision()); key.putInt("consumptionQuantity", step.consumptionQuantity());
        var slots = new net.minecraft.nbt.CompoundTag(); step.sourceCounts().forEach((slot, count) -> slots.putInt(Integer.toString(slot), count));
        key.put("sourceCounts", slots);
        return FrontierV3OperationFingerprint.of(key);
    }
    static FrontierV3OperationReceipt.Declaration take(ResidentMeal meal, ResidentMealPhysicalStep step,
            FrontierV3MinecraftOperation.Participant actor, FrontierV3MinecraftOperation.Participant source) {
        if (step.phase() != ResidentMeal.Phase.TAKE) throw new IllegalArgumentException("take declaration has a foreign food step");
        String key = key(meal, step);
        return new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MEAL_TAKE, meal.residentId(), key,
                key + ":" + meal.depotId().value() + ":" + meal.portion().itemKind() + ":" + meal.portion().quantity(),
                Set.of(actor.identity(), source.identity()));
    }
    static FrontierV3OperationReceipt.Declaration consume(ResidentMeal meal, ResidentMealPhysicalStep step,
            FoodPortion held, java.util.List<FrontierV3MinecraftOperation.Participant> participants) {
        if (step.phase() != ResidentMeal.Phase.CONSUME) throw new IllegalArgumentException("consume declaration has a foreign food step");
        String key = key(meal, step);
        return new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MEAL_CONSUME, meal.residentId(), key,
                key + ":" + held.itemKind() + ":" + held.quantity(), participants.stream().map(FrontierV3MinecraftOperation.Participant::identity)
                    .collect(java.util.stream.Collectors.toSet()));
    }
}
