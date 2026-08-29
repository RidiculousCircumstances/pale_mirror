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
import io.farfrontier.palemirror.frontier.v3.model.CargoLoadObservation;
import io.farfrontier.palemirror.frontier.v3.model.CargoLoadingStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.Optional;

/**
 * Durable exact removal of one active-depot stack before the pure state transfers it to cargo.
 *
 * <p>RUNNING is deliberately inspection-only: a restart never removes a stack again.  A blank
 * exact slot proves the physical removal and can confirm; any present, changed or foreign stack
 * becomes a visible unknown outcome for the contract instead of a replay or overwrite.</p>
 */
final class FrontierV3CargoLoadingExecutor {
    private FrontierV3CargoLoadingExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.CARGO_LOADING)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, PhysicalIntent intent) {
        CargoLoadingStateSupport.Target target;
        try { target = CargoLoadingStateSupport.target(state, intent); }
        catch (IllegalArgumentException conflict) { unknown(runtime, intent.id(), "canonical-precondition-conflict"); return; }
        BlockPos position = new BlockPos(target.chestPosition().x(), target.chestPosition().y(), target.chestPosition().z());
        if (!level.hasChunkAt(position)) return;
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(position, target.slot().containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(runtime, intent, target, chest); return; }
        if (!matches(chest, target)) { unknown(runtime, intent.id(), "stack-precondition-conflict"); return; }
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
        if (!remove(chest, target)) { unknown(runtime, intent.id(), "physical-removal-conflict"); return; }
        confirm(runtime, intent, target);
    }

    static boolean matches(ChestBlockEntity chest, CargoLoadingStateSupport.Target target) {
        return FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.slot().slot()), target.item());
    }

    static boolean remove(ChestBlockEntity chest, CargoLoadingStateSupport.Target target) {
        if (!matches(chest, target)) return false;
        chest.setItem(target.slot().slot(), ItemStack.EMPTY); chest.setChanged();
        return chest.getItem(target.slot().slot()).isEmpty();
    }

    private static void inspectRunning(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                       CargoLoadingStateSupport.Target target, ChestBlockEntity chest) {
        if (chest.getItem(target.slot().slot()).isEmpty()) confirm(runtime, intent, target);
        else unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    private static void confirm(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                CargoLoadingStateSupport.Target target) {
        CargoLoadObservation observation = new CargoLoadObservation(
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), target.contract().id(),
                target.contract().cargoId(), target.item().id(), target.item().count());
        if (!transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed")) {
            throw new IllegalStateException("cargo loading confirmation was rejected");
        }
    }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id, String phase) {
        transition(runtime, id, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId id,
                                      PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId command = new CommandId("executor:cargo-load-" + phase + "-" + id.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, command, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(command), new PhysicalIntentTransition(id, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }
}
