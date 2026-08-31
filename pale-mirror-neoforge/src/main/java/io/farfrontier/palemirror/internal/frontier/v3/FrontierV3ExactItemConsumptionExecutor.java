package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemConsumptionStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemConsumedObservation;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.Optional;

/** Removes one exact identity-tagged count only after durable admission and loaded-world inspection. */
final class FrontierV3ExactItemConsumptionExecutor {
    private FrontierV3ExactItemConsumptionExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        Target target = target(state, intent);
        if (target == null) { if (intent.status() == PhysicalIntentStatus.RUNNING) unknown(runtime, intent.id(), "target-conflict"); return; }
        if (!level.hasChunkAt(target.chestPosition())) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level, new FrontierV3CargoHandoffExecutor.StoreTarget(target.chestPosition(), target.containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(runtime, intent, target, chest); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!consume(chest, target)) { unknown(runtime, intent.id(), "precondition-conflict"); return; }
        confirm(runtime, intent, target);
    }

    static boolean consume(ChestBlockEntity chest, Target target) {
        ItemStack stack = chest.getItem(target.slot());
        if (!FrontierV3CargoHandoffExecutor.exactMatch(stack, target.item())) return false;
        stack.shrink(target.count()); chest.setItem(target.slot(), stack); chest.setChanged(); return true;
    }

    /** Restart predicate: the same identity tag with exactly the expected remainder is the only success. */
    static boolean consumed(ChestBlockEntity chest, Target target) {
        ItemStack stack = chest.getItem(target.slot()); int remainder = target.item().count() - target.count();
        return remainder == 0 ? stack.isEmpty() : FrontierV3CargoHandoffExecutor.exactMatch(stack, new ExactItemStack(
                target.item().id(), target.item().economicOwnerId(), target.item().itemKind(), remainder, target.item().custody()));
    }

    private static void inspectRunning(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target, ChestBlockEntity chest) {
        if (consumed(chest, target)) confirm(runtime, intent, target);
        else unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Target target) {
        ExactItemConsumedObservation observation = new ExactItemConsumedObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), target.item().id(), target.item().count(), target.item().count() - target.count());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed")) throw new IllegalStateException("exact consumption confirmation was rejected");
    }

    static Target target(FrontierWorldState state, PhysicalIntent intent) {
        try {
            ExactItemConsumptionStateSupport.Claim claim = ExactItemConsumptionStateSupport.claim(state, intent);
            return new Target(claim.item(), claim.containerId(), claim.slot(), new BlockPos(claim.position().x(), claim.position().y(), claim.position().z()), claim.count());
        } catch (IllegalArgumentException conflict) {
            return null;
        }
    }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) { transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase); }
    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:exact-consumption-" + phase + "-" + id.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }

    record Target(ExactItemStack item, SubjectId containerId, int slot, BlockPos chestPosition, int count) { }
}
