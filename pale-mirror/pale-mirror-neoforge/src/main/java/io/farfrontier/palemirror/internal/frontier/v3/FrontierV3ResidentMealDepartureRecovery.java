package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;

/** Food owner drains an exact unissued/unattempted effect after unload; never awards a portion. */
final class FrontierV3ResidentMealDepartureRecovery {
    private FrontierV3ResidentMealDepartureRecovery() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        for (var meal : state.humanPopulation().meals().values()) {
            var step = meal.pendingPhysicalStep().orElse(null);
            if (step == null) continue;
            var body = FrontierV3DepartedBodyEvidence.retainedBody(state, meal.residentId()).orElse(null);
            if (body == null || !FrontierV3DepartedBodyEvidence.absent(level, runtime, state, body)) continue;
            var protocol = step.phase() == ResidentMeal.Phase.TAKE ? FrontierV3OperationReceipt.Protocol.MEAL_TAKE
                    : FrontierV3OperationReceipt.Protocol.MEAL_CONSUME;
            var prepared = new ResidentMealHotEffectPrepared(meal.residentId(), step);
            // A retained positive consequence is authoritative and must finish before considering
            // cancellation. No reconstruction from matching stack counts after an attempted action.
            if (FrontierV3OperationConfirmation.resumeBound(level, runtime, protocol, meal.residentId(), prepared)) return;
            var unused = FrontierV3MinecraftOperation.cancelUnusedPreparation(level, protocol, meal.residentId(), prepared);
            if (unused.isEmpty()) continue;
            var cancelled = new ResidentMealHotEffectCancelled(body, step);
            if (unused.orElseThrow().cancelledTicket().isPresent())
                FrontierV3OperationConfirmation.submitRecovery(level, runtime,
                        unused.orElseThrow().cancelledTicket().orElseThrow(), cancelled, Optional.empty());
            else FrontierV3CommandSubmission.submit(runtime, "resident-meal-departure-cancel", meal.residentId().value(), cancelled);
            return;
        }
    }
}
