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
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientArrivalObservation;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientDepartureObservation;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientTransfer;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.Optional;

/**
 * One loaded hive-organ endpoint boundary.  It never requires both nests to be loaded: source
 * removal confirms COLD cargo custody, and the later target visit confirms exact insertion.
 * A RUNNING intent only inspects its one named chest; it never writes again after a restart.
 */
final class FrontierV3HiveNutrientEndpointExecutor {
    private FrontierV3HiveNutrientEndpointExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null); if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE || intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> execute(level, runtime, state, intent));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state, PhysicalIntent intent) {
        Endpoint target;
        try { target = endpoint(state, intent); }
        catch (IllegalArgumentException invalid) { unknown(runtime, intent.id(), "canonical-precondition-conflict"); return; }
        if (!level.hasChunkAt(target.position())) return;
        if (target.surface().status() == ContainerSurfaceStatus.UNMATERIALIZED || target.surface().status() == ContainerSurfaceStatus.PREPARED) return;
        if (target.surface().status() == ContainerSurfaceStatus.CONFLICT) { unknown(runtime, intent.id(), "surface-conflict"); return; }
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.activeChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(target.position(), target.containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target, chest); return; }
        if (!precondition(intent, target, chest)) { unknown(runtime, intent.id(), "stack-precondition-conflict"); return; }
        if (!(transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running") instanceof CommandResult.Accepted)) return;
        if (!effect(intent, target, chest)) { unknown(runtime, intent.id(), "physical-effect-conflict"); return; }
        confirm(level, runtime, intent, target);
    }

    private static Endpoint endpoint(FrontierWorldState state, PhysicalIntent intent) {
        HiveNutrientTransfer transfer = state.hiveColony().nutrientTransfers().values().stream()
                .filter(value -> intent.subjectIds().contains(value.id())).findFirst().orElse(null);
        if (transfer == null || !transfer.endpointIntentId().equals(Optional.of(intent.id()))
                || !intent.subjectIds().equals(java.util.List.of(transfer.id(), transfer.cargoId(), transfer.itemId()))) {
            throw new IllegalArgumentException("hive nutrient intent has no exact retained transfer");
        }
        boolean departure = intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE;
        SubjectId containerId = departure ? transfer.sourceStoreId() : transfer.targetStoreId();
        int slot = departure ? transfer.sourceSlot().slot() : transfer.targetSlot().slot();
        ContainerSurface surface = state.inventory().surfaces().get(containerId);
        ExactItemStack item = state.inventory().items().get(transfer.itemId());
        if (surface == null || item == null || (departure && !item.custody().equals(transfer.sourceSlot()))
                || (!departure && !item.custody().equals(new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.Cargo(transfer.cargoId())))) {
            throw new IllegalArgumentException("hive nutrient endpoint has no retained exact item custody");
        }
        return new Endpoint(transfer, surface, containerId, slot, item, new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()));
    }

    private static boolean precondition(PhysicalIntent intent, Endpoint target, ChestBlockEntity chest) {
        return intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE
                ? matchesDeparture(chest, target.slot(), target.item()) : matchesArrival(chest, target.slot());
    }

    private static boolean effect(PhysicalIntent intent, Endpoint target, ChestBlockEntity chest) {
        return intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE ? removeExact(chest, target.slot(), target.item())
                : insertExact(chest, target.slot(), target.item());
    }

    private static void inspectRunning(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                       Endpoint target, ChestBlockEntity chest) {
        boolean observed = intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE ? chest.getItem(target.slot()).isEmpty()
                : FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(target.slot()), target.item());
        if (observed) confirm(level, runtime, intent, target); else unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    static boolean matchesDeparture(ChestBlockEntity chest, int slot, ExactItemStack item) {
        return slot >= 0 && slot < chest.getContainerSize() && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(slot), item);
    }
    static boolean matchesArrival(ChestBlockEntity chest, int slot) { return slot >= 0 && slot < chest.getContainerSize() && chest.getItem(slot).isEmpty(); }
    static boolean removeExact(ChestBlockEntity chest, int slot, ExactItemStack item) {
        if (!matchesDeparture(chest, slot, item)) return false;
        chest.setItem(slot, ItemStack.EMPTY); chest.setChanged(); return chest.getItem(slot).isEmpty();
    }
    static boolean insertExact(ChestBlockEntity chest, int slot, ExactItemStack item) {
        if (!matchesArrival(chest, slot)) return false;
        chest.setItem(slot, FrontierV3CargoHandoffExecutor.materializedStack(item)); chest.setChanged();
        return FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(slot), item);
    }

    private static void confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Endpoint target) {
        PhysicalObservationId observationId = new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-'));
        PhysicalEffectObservation observation = intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE
                ? new HiveNutrientDepartureObservation(observationId, intent.id(), target.transfer().id(), target.transfer().cargoId(), target.item().id(), target.item().count())
                : new HiveNutrientArrivalObservation(observationId, intent.id(), target.transfer().id(), target.transfer().cargoId(), target.item().id(), target.item().count());
        CommandResult result = transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed");
        if (!(result instanceof CommandResult.Accepted)) {
            throw new IllegalStateException("hive nutrient endpoint confirmation was rejected");
        }
        String kind = intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE ? "hive_nutrient_departed" : "hive_nutrient_arrived";
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.hiveNutrientCorrelation(target.transfer().id()), kind, target.transfer().id(), result);
    }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId, String phase) {
        transition(runtime, intentId, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }

    private static CommandResult transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId,
                                      PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId commandId = new CommandId("executor:hive-nutrient-" + phase + "-" + intentId.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), new PhysicalIntentTransition(intentId, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result;
    }

    private record Endpoint(HiveNutrientTransfer transfer, ContainerSurface surface, SubjectId containerId, int slot,
                            ExactItemStack item, BlockPos position) { }
}
