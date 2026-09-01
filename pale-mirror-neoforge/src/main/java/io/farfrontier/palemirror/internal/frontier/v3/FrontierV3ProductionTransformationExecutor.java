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
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ProductionTransformationObservation;
import io.farfrontier.palemirror.frontier.v3.model.ProductionTransformationStateSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.Optional;

/** Durable loaded-chunk transformation of one owned wheat stack into its exact bread output. */
final class FrontierV3ProductionTransformationExecutor {
    private FrontierV3ProductionTransformationExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, PhysicalIntent intent) {
        ProductionTransformationStateSupport.Target target;
        try { target = ProductionTransformationStateSupport.target(state, intent); }
        catch (IllegalArgumentException conflict) { unknown(runtime, intent.id(), "canonical-precondition-conflict"); return; }
        BlockPos position = new BlockPos(target.chestPosition().x(), target.chestPosition().y(), target.chestPosition().z());
        if (!level.hasChunkAt(position)) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(position, target.slot().containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectOrApply(runtime, intent, target, chest); return; }
        if (!matchesInput(chest, target)) { unknown(runtime, intent.id(), "input-precondition-conflict"); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!replace(chest, target)) { unknown(runtime, intent.id(), "physical-write-conflict"); return; }
        confirm(runtime, intent, target);
    }

    private static void inspectOrApply(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                       ProductionTransformationStateSupport.Target target, ChestBlockEntity chest) {
        if (matchesOutput(chest, target)) { confirm(runtime, intent, target); return; }
        if (matchesInput(chest, target) && replace(chest, target)) { confirm(runtime, intent, target); return; }
        unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    static boolean matchesInput(ChestBlockEntity chest, ProductionTransformationStateSupport.Target target) {
        return FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.slot().slot()), target.input());
    }
    static boolean matchesOutput(ChestBlockEntity chest, ProductionTransformationStateSupport.Target target) {
        return FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.slot().slot()), target.output());
    }
    static boolean replace(ChestBlockEntity chest, ProductionTransformationStateSupport.Target target) {
        if (!matchesInput(chest, target)) return false;
        ItemStack output = FrontierV3CargoHandoffExecutor.materializedStack(target.output());
        chest.setItem(target.slot().slot(), output); chest.setChanged(); return matchesOutput(chest, target);
    }
    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                ProductionTransformationStateSupport.Target target) {
        ProductionTransformationObservation observation = new ProductionTransformationObservation(
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), target.input().id(), target.output().id(),
                target.input().count(), target.output().count());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed")) {
            throw new IllegalStateException("production transformation confirmation was rejected");
        }
    }
    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }
    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, PhysicalIntentStatus status,
                                      Optional<PhysicalEffectObservation> observation, String phase) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:production-transform-" + phase + "-" + id.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }
}
