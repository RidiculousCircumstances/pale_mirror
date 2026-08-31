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
import io.farfrontier.palemirror.frontier.v3.model.CargoBatch;
import io.farfrontier.palemirror.frontier.v3.model.CargoHandoffObservation;
import io.farfrontier.palemirror.frontier.v3.model.CargoHandoffPlacement;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.HiveOrgan;
import io.farfrontier.palemirror.frontier.v3.model.HiveOrganKind;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Loaded-chunk physical executor for the first v3 cargo hand-off vertical slice.
 *
 * <p>It never loads chunks or replaces a non-owned block. A RUNNING effect is only inspected
 * after restart; it is never written again, so an unknown player/world change cannot become a
 * blind replay.</p>
 */
final class FrontierV3CargoHandoffExecutor {
    static final String CONTAINER_ID_KEY = "pale_mirror_frontier_v3_container";
    static final String ITEM_ID_KEY = "pale_mirror_frontier_v3_item";
    static final String PENDING_INGRESS_KEY = "pale_mirror_frontier_v3_pending_ingress";
    static final String WORLD_CARRIER_ID_KEY = "pale_mirror_frontier_v3_world_carrier";

    private FrontierV3CargoHandoffExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.CARGO_HANDOFF)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .findFirst().ifPresent(intent -> executeOne(level, runtime, state, intent));
    }

    private static void executeOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                   FrontierWorldState state, PhysicalIntent intent) {
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null) throw new IllegalStateException("cargo hand-off intent has no operation");
        StoreTarget target = target(state, operation);
        if (!level.hasChunkAt(target.position())) return;
        ContainerSurface surface = state.inventory().surfaces().get(target.containerId());
        if (surface.status() == ContainerSurfaceStatus.UNMATERIALIZED || surface.status() == ContainerSurfaceStatus.PREPARED) return;
        if (surface.status() == ContainerSurfaceStatus.CONFLICT) {
            transition(runtime, intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), "conflict");
            return;
        }
        ChestBlockEntity chest = activeChest(level, target);
        if (chest == null) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, target.containerId());
            return;
        }
        if (intent.status() == PhysicalIntentStatus.PREPARED) {
            if (!transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running")) return;
            if (!writeInitialCargo(chest, state, operation.cargoId())) {
                transition(runtime, intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), "conflict");
                return;
            }
            observation(chest, state, intent, operation.cargoId(), target.containerId())
                    .ifPresentOrElse(observed -> transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observed), "confirmed"),
                            () -> transition(runtime, intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), "conflict"));
            return;
        }
        Optional<CargoHandoffObservation> observed = observation(chest, state, intent, operation.cargoId(), target.containerId());
        if (observed.isPresent()) {
            transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, observed.map(value -> (PhysicalEffectObservation) value), "confirmed");
        } else {
            transition(runtime, intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), "conflict");
        }
    }

    /** Compatibility test helper; container-surface ownership is implemented separately. */
    static ChestBlockEntity claimFreshChest(ServerLevel level, StoreTarget target) {
        return FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, target.position(), target.containerId());
    }

    /** Compatibility test helper; production execution only calls it through PREPARED ownership. */
    static ChestBlockEntity ownedChest(ServerLevel level, StoreTarget target) { return claimFreshChest(level, target); }

    static ChestBlockEntity activeChest(ServerLevel level, StoreTarget target) {
        return FrontierV3ContainerSurfaceExecutor.activeChest(level, target.position(), target.containerId());
    }

    private static boolean writeInitialCargo(ChestBlockEntity chest, FrontierWorldState state, SubjectId cargoId) {
        CargoBatch cargo = state.inventory().cargo().get(cargoId);
        if (cargo == null) throw new IllegalStateException("prepared cargo hand-off has no cargo");
        List<ExactItemStack> items = cargo.itemIds().stream().map(state.inventory().items()::get)
                .sorted(Comparator.comparing(ExactItemStack::id)).toList();
        for (int index = 0; index < items.size(); index++) {
            if (!chest.getItem(index).isEmpty()) return false;
        }
        for (int index = 0; index < items.size(); index++) chest.setItem(index, materializedStack(items.get(index)));
        chest.setChanged();
        return true;
    }

    private static Optional<CargoHandoffObservation> observation(ChestBlockEntity chest, FrontierWorldState state,
                                                                   PhysicalIntent intent, SubjectId cargoId, SubjectId containerId) {
        CargoBatch cargo = state.inventory().cargo().get(cargoId);
        if (cargo == null) return Optional.empty();
        List<ExactItemStack> items = cargo.itemIds().stream().map(state.inventory().items()::get)
                .sorted(Comparator.comparing(ExactItemStack::id)).toList();
        List<CargoHandoffPlacement> placements = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ExactItemStack expected = items.get(index); ItemStack actual = chest.getItem(index);
            if (!exactMatch(actual, expected)) return Optional.empty();
            placements.add(new CargoHandoffPlacement(expected.id(), new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot(containerId, index)));
        }
        return Optional.of(new CargoHandoffObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                intent.id(), cargoId, placements));
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId,
                                      PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId commandId = new CommandId("executor:" + phase + "-" + intentId.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId),
                new PhysicalIntentTransition(intentId, status, observation))).orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }

    private static StoreTarget target(FrontierWorldState state, RouteOperation operation) {
        var nest = state.bootstrap().hive().seedNests().getFirst();
        HiveOrgan store = state.bootstrap().hive().organs().stream().filter(organ -> organ.nestId().equals(nest.id()) && organ.kind() == HiveOrganKind.STORE)
                .findFirst().orElseThrow(() -> new IllegalStateException("hive nest lacks a store organ"));
        SubjectId container = store.containerId().orElseThrow(() -> new IllegalStateException("store organ lacks receiver container"));
        ContainerSurface surface = state.inventory().surfaces().get(container);
        if (surface == null) throw new IllegalStateException("store container has no physical surface");
        return new StoreTarget(new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()), container);
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.decodedState().orElse(null);
    }
    static ItemStack materializedStack(ExactItemStack item) {
        Item minecraftItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.itemKind()));
        if (minecraftItem == Items.AIR) throw new IllegalStateException("unknown Minecraft item for exact hand-off: " + item.itemKind());
        ItemStack stack = new ItemStack(minecraftItem, item.count());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(ITEM_ID_KEY, item.id().value()));
        return stack;
    }
    static boolean exactMatch(ItemStack actual, ExactItemStack expected) {
        if (actual.getCount() != expected.count() || !BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(expected.itemKind())) return false;
        CustomData data = actual.get(DataComponents.CUSTOM_DATA);
        return data != null && expected.id().value().equals(data.copyTag().getString(ITEM_ID_KEY));
    }
    static Optional<SubjectId> itemId(ItemStack actual) {
        CustomData data = actual.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        String value = data.copyTag().getString(ITEM_ID_KEY);
        if (value.isBlank()) return Optional.empty();
        try { return Optional.of(new SubjectId(value)); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    static void bindExactItemId(ItemStack stack, SubjectId itemId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(ITEM_ID_KEY, itemId.value()));
    }
    static void markPendingIngress(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(PENDING_INGRESS_KEY, true));
    }
    static boolean pendingIngress(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(PENDING_INGRESS_KEY);
    }
    static void clearPendingIngress(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(PENDING_INGRESS_KEY));
    }
    static void bindWorldCarrier(ItemStack stack, java.util.UUID carrierId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(WORLD_CARRIER_ID_KEY, carrierId));
    }
    static Optional<java.util.UUID> worldCarrierId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().hasUUID(WORLD_CARRIER_ID_KEY)) return Optional.empty();
        return Optional.of(data.copyTag().getUUID(WORLD_CARRIER_ID_KEY));
    }
    record StoreTarget(BlockPos position, SubjectId containerId) { }
}
