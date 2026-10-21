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
        return FrontierV3OperationConfirmation.fingerprint(new ResidentMealHotEffectPrepared(resident, step));
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
            FoodPortion held, FrontierV3MinecraftOperation.Participant actor) {
        if (step.phase() != ResidentMeal.Phase.CONSUME) throw new IllegalArgumentException("consume declaration has a foreign food step");
        String key = key(meal, step);
        return new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.MEAL_CONSUME, meal.residentId(), key,
                key + ":" + held.itemKind() + ":" + held.quantity(), Set.of(actor.identity()));
    }
}
