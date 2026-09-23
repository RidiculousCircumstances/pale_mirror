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
import io.farfrontier.palemirror.frontier.v3.model.FungibleCargoHandoffObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
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
            if (!writeInitialCargo(chest, state, operation.cargoId(), target.containerId())) {
                transition(runtime, intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), "conflict");
                return;
            }
            observation(chest, state, intent, operation.cargoId(), target.containerId())
                    .ifPresentOrElse(observed -> transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observed), "confirmed"),
                            () -> transition(runtime, intent.id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), "conflict"));
            return;
        }
        Optional<PhysicalEffectObservation> observed = observation(chest, state, intent, operation.cargoId(), target.containerId());
        if (observed.isPresent()) {
            transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, observed, "confirmed");
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

    private static boolean writeInitialCargo(ChestBlockEntity chest, FrontierWorldState state, SubjectId cargoId, SubjectId targetContainerId) {
        CargoBatch cargo = state.inventory().cargo().get(cargoId);
        if (cargo == null) throw new IllegalStateException("prepared cargo hand-off has no cargo");
        if (cargo.fungibleContents()) return writeInitialFungibleCargo(chest, state, cargo, targetContainerId);
        List<ExactItemStack> items = cargo.itemIds().stream().map(state.inventory().items()::get)
                .sorted(Comparator.comparing(ExactItemStack::id)).toList();
        for (int index = 0; index < items.size(); index++) {
            if (!chest.getItem(index).isEmpty()) return false;
        }
        for (int index = 0; index < items.size(); index++) chest.setItem(index, materializedStack(items.get(index)));
        chest.setChanged();
        return true;
    }

    private static boolean writeInitialFungibleCargo(ChestBlockEntity chest, FrontierWorldState state, CargoBatch cargo, SubjectId targetContainerId) {
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        var source = resources.accounts().values().stream().filter(account -> account.custody()
                instanceof io.farfrontier.palemirror.frontier.v3.model.ResourceCustody.Cargo custody && custody.cargoId().equals(cargo.id())).findFirst().orElse(null);
        if (source == null || resources.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(source.id()))) return false;
        var receiver = resources.accounts().values().stream().filter(account -> account.custody()
                instanceof io.farfrontier.palemirror.frontier.v3.model.ResourceCustody.Container container && container.containerId().equals(targetContainerId)).findFirst().orElse(null);
        List<FungiblePhysicalObservation.Stack> before = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, targetContainerId);
        if (receiver == null) {
            if (!before.isEmpty()) return false;
        } else {
            long epoch = receiverEpoch(resources, receiver.id());
            try { FungiblePhysicalObservation.bind(resources, receiver.id(), epoch, before); }
            catch (IllegalArgumentException invalid) { return false; }
        }
        java.util.Map<String, Integer> quantities = new java.util.TreeMap<>();
        source.lotQuantities().forEach((lotId, quantity) -> quantities.merge(resources.lots().get(lotId).itemKind(), quantity, Integer::sum));
        int slot = 0;
        for (var entry : quantities.entrySet()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return false;
            for (int remaining = entry.getValue(); remaining > 0; remaining -= Math.min(64, remaining)) {
                while (slot < chest.getContainerSize() && !chest.getItem(slot).isEmpty()) slot++;
                if (slot == chest.getContainerSize()) return false;
                chest.setItem(slot++, new ItemStack(BuiltInRegistries.ITEM.get(itemId), Math.min(64, remaining)));
            }
        }
        chest.setChanged();
        return true;
    }

    private static Optional<PhysicalEffectObservation> observation(ChestBlockEntity chest, FrontierWorldState state,
                                                                     PhysicalIntent intent, SubjectId cargoId, SubjectId containerId) {
        CargoBatch cargo = state.inventory().cargo().get(cargoId);
        if (cargo == null) return Optional.empty();
        if (cargo.fungibleContents()) return fungibleObservation(chest, state, intent, cargo, containerId).map(value -> value);
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

    private static Optional<FungibleCargoHandoffObservation> fungibleObservation(ChestBlockEntity chest, FrontierWorldState state,
                                                                                     PhysicalIntent intent, CargoBatch cargo, SubjectId containerId) {
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        var account = resources.accounts().values().stream().filter(value -> value.custody()
                instanceof io.farfrontier.palemirror.frontier.v3.model.ResourceCustody.Cargo custody && custody.cargoId().equals(cargo.id())).findFirst().orElse(null);
        if (account == null || resources.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(account.id()))) return Optional.empty();
        var receiver = resources.accounts().values().stream().filter(value -> value.custody()
                instanceof io.farfrontier.palemirror.frontier.v3.model.ResourceCustody.Container container && container.containerId().equals(containerId)).findFirst().orElse(null);
        long epoch = receiver == null ? 1L : receiverEpoch(resources, receiver.id());
        List<FungiblePhysicalObservation.Stack> stacks = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, containerId);
        try {
            if (receiver == null) FungiblePhysicalObservation.bind(resources, account.id(), epoch, stacks);
            return Optional.of(new FungibleCargoHandoffObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')),
                    intent.id(), cargo.id(), epoch, stacks));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static long receiverEpoch(FungibleResourceLedger resources, SubjectId receiverAccountId) {
        List<Long> epochs = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(receiverAccountId))
                .map(io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding::authorityEpoch).distinct().toList();
        if (epochs.size() != 1 || epochs.getFirst() < 1) throw new IllegalStateException("fungible receiver has no one current physical authority");
        return epochs.getFirst();
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId,
                                      PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
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
    /**
     * Exact postcondition for a one-unit physical extraction from a named stack.
     *
     * <p>A surviving remainder must retain both the original Minecraft kind and its exact
     * canonical identity. Matching only the copied item tag would allow an altered physical
     * item to confirm a durable source-to-cargo transfer after a restart.</p>
     */
    static boolean exactOneUnitDecrement(ItemStack actual, ExactItemStack source) {
        if (source.count() == 1) return actual.isEmpty();
        return exactMatch(actual, new ExactItemStack(source.id(), source.economicOwnerId(), source.itemKind(), source.count() - 1, source.custody()));
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
    static boolean hasWorldCarrierDeclaration(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(WORLD_CARRIER_ID_KEY);
    }
    record StoreTarget(BlockPos position, SubjectId containerId) { }
}
