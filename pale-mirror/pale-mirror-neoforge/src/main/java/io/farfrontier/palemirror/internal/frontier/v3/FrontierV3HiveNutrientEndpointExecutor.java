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
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungibleNutrientDepartureObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientArrivalObservation;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientDepartureObservation;
import io.farfrontier.palemirror.frontier.v3.model.HiveNutrientTransfer;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalEffectObservation;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
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
        FungibleDeparture fungibleDeparture = fungibleDeparture(state, intent);
        if (fungibleDeparture != null) {
            executeFungibleDeparture(level, runtime, state, intent, fungibleDeparture);
            return;
        }
        FungibleArrival fungibleArrival = fungibleArrival(state, intent);
        if (fungibleArrival != null) {
            executeFungibleArrival(level, runtime, state, intent, fungibleArrival);
            return;
        }
        Endpoint target;
        try { target = endpoint(state, intent); }
        catch (IllegalArgumentException invalid) { unknown(runtime, intent.id(), "canonical-precondition-conflict"); return; }
        if (!level.hasChunkAt(target.position())) return;
        if (ReferenceContainerCustody.isReferenceContainer(state, target.containerId())
                && !ReferenceContainerCustody.hasOperationalCustody(state, target.containerId())) return;
        if (target.surface().status() == ContainerSurfaceStatus.UNMATERIALIZED || target.surface().status() == ContainerSurfaceStatus.PREPARED) return;
        if (target.surface().status() == ContainerSurfaceStatus.CONFLICT) { unknown(runtime, intent.id(), "surface-conflict"); return; }
        ChestBlockEntity chest = FrontierV3ExactItemPresentation.activeChest(level,
                new FrontierV3ExactItemPresentation.StoreTarget(target.position(), target.containerId()));
        if (chest == null) { unknown(runtime, intent.id(), "chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) { inspectRunning(level, runtime, intent, target, chest); return; }
        if (!precondition(intent, target, chest)) { unknown(runtime, intent.id(), "stack-precondition-conflict"); return; }
        if (!(transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "running") instanceof CommandResult.Accepted)) return;
        if (!effect(intent, target, chest)) { unknown(runtime, intent.id(), "physical-effect-conflict"); return; }
        confirm(level, runtime, intent, target, chest);
    }

    private static FungibleDeparture fungibleDeparture(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE) return null;
        HiveNutrientTransfer transfer = state.hiveColony().nutrientTransfers().values().stream()
                .filter(value -> value.fungibleContents() && value.endpointIntentId().equals(Optional.of(intent.id()))).findFirst().orElse(null);
        if (transfer == null || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.nutrientDeparture(transfer.id(), transfer.cargoId(), transfer.itemId()))) return null;
        List<CustodyAccount> accounts = state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container container && container.containerId().equals(transfer.sourceStoreId())).toList();
        if (accounts.size() != 1) return null;
        CustodyAccount account = accounts.getFirst(); ResourceLot lot = state.inventory().fungibleResources().lots().get(transfer.itemId());
        List<PhysicalStackBinding> current = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(account.id())).toList();
        if (lot == null || account.lotQuantities().getOrDefault(lot.id(), 0) < 64 || current.isEmpty()) return null;
        long epoch = current.getFirst().authorityEpoch();
        if (current.stream().anyMatch(binding -> binding.authorityEpoch() != epoch)) return null;
        PhysicalStackBinding source = current.stream().filter(binding -> binding.lotQuantities().equals(java.util.Map.of(lot.id(), 64))
                && binding.claimQuantities().isEmpty() && binding.address() instanceof PhysicalStackAddress.ContainerSlot).findFirst().orElse(null);
        if (source == null) return null;
        InventoryCustody.ContainerSlot slot = ((PhysicalStackAddress.ContainerSlot) source.address()).slot();
        ContainerSurface surface = state.inventory().surfaces().get(transfer.sourceStoreId());
        if (surface == null || !slot.containerId().equals(transfer.sourceStoreId())) return null;
        return new FungibleDeparture(transfer, account, lot, source, epoch, surface,
                new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()), slot.slot());
    }

    private static void executeFungibleDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                 FrontierWorldState state, PhysicalIntent intent, FungibleDeparture target) {
        if (!level.hasChunkAt(target.position())) return;
        if (ReferenceContainerCustody.isReferenceContainer(state, target.transfer().sourceStoreId())
                && !ReferenceContainerCustody.hasOperationalCustody(state, target.transfer().sourceStoreId())) return;
        if (target.surface().status() != ContainerSurfaceStatus.ACTIVE) return;
        ChestBlockEntity chest = FrontierV3ExactItemPresentation.activeChest(level,
                new FrontierV3ExactItemPresentation.StoreTarget(target.position(), target.transfer().sourceStoreId()));
        if (chest == null) { unknown(runtime, intent.id(), "fungible-chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) {
            if (matchesFungibleDeparture(chest, target)) confirmFungibleDeparture(runtime, intent, target, chest);
            else unknown(runtime, intent.id(), "fungible-restart-postcondition-conflict");
            return;
        }
        if (!matchesFungibleSource(chest, target)) { unknown(runtime, intent.id(), "fungible-stack-precondition-conflict"); return; }
        if (!(transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "fungible-running") instanceof CommandResult.Accepted)) return;
        chest.setItem(target.slot(), ItemStack.EMPTY); chest.setChanged();
        if (!matchesFungibleDeparture(chest, target)) { unknown(runtime, intent.id(), "fungible-physical-effect-conflict"); return; }
        confirmFungibleDeparture(runtime, intent, target, chest);
    }

    private static boolean matchesFungibleSource(ChestBlockEntity chest, FungibleDeparture target) {
        if (target.slot() < 0 || target.slot() >= chest.getContainerSize()) return false;
        ItemStack stack = chest.getItem(target.slot());
        return stack.getCount() == 64 && target.lot().itemKind().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static boolean matchesFungibleDeparture(ChestBlockEntity chest, FungibleDeparture target) {
        return target.slot() >= 0 && target.slot() < chest.getContainerSize() && chest.getItem(target.slot()).isEmpty();
    }

    private static void confirmFungibleDeparture(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                                 FungibleDeparture target, ChestBlockEntity chest) {
        FrontierWorldState current = runtime.decodedState().orElseThrow();
        List<FungiblePhysicalObservation.Stack> remaining = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, current, target.transfer().sourceStoreId());
        PhysicalObservationId observationId = new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-'));
        PhysicalEffectObservation observation = new FungibleNutrientDepartureObservation(observationId, intent.id(), target.transfer().id(),
                target.transfer().cargoId(), target.account().id(), target.lot().id(), 64, target.authorityEpoch(), remaining);
        if (!(transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "fungible-confirmed") instanceof CommandResult.Accepted)) {
            throw new IllegalStateException("fungible hive nutrient departure confirmation was rejected");
        }
    }

    private static FungibleArrival fungibleArrival(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL) return null;
        HiveNutrientTransfer transfer = state.hiveColony().nutrientTransfers().values().stream()
                .filter(value -> value.fungibleContents() && value.endpointIntentId().equals(Optional.of(intent.id()))).findFirst().orElse(null);
        if (transfer == null || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.nutrientArrival(transfer.id(), transfer.cargoId(), transfer.itemId()))) return null;
        CustodyAccount cargo = state.inventory().fungibleResources().accounts().values().stream().filter(account -> account.custody()
                instanceof ResourceCustody.Cargo held && held.cargoId().equals(transfer.cargoId())).findFirst().orElse(null);
        ContainerSurface surface = state.inventory().surfaces().get(transfer.targetStoreId());
        if (cargo == null || surface == null || cargo.lotQuantities().getOrDefault(transfer.itemId(), 0) != 64) return null;
        ResourceLot lot = state.inventory().fungibleResources().lots().get(transfer.itemId());
        if (lot == null) return null;
        CustodyAccount receiver = state.inventory().fungibleResources().accounts().values().stream().filter(account -> account.custody()
                instanceof ResourceCustody.Container held && held.containerId().equals(transfer.targetStoreId())).findFirst().orElse(null);
        long epoch = receiver == null ? 1L : receiverEpoch(state, receiver);
        if (epoch < 1) return null;
        return new FungibleArrival(transfer, cargo, lot, receiver, epoch, surface,
                new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()));
    }

    private static long receiverEpoch(FrontierWorldState state, CustodyAccount receiver) {
        List<Long> epochs = state.inventory().fungibleResources().bindings().values().stream().filter(binding -> binding.accountId().equals(receiver.id()))
                .map(PhysicalStackBinding::authorityEpoch).distinct().toList();
        return epochs.size() == 1 ? epochs.getFirst() : -1L;
    }

    private static void executeFungibleArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                               FrontierWorldState state, PhysicalIntent intent, FungibleArrival target) {
        if (!level.hasChunkAt(target.position())) return;
        if (ReferenceContainerCustody.isReferenceContainer(state, target.transfer().targetStoreId())
                && !ReferenceContainerCustody.hasOperationalCustody(state, target.transfer().targetStoreId())) return;
        if (target.surface().status() != ContainerSurfaceStatus.ACTIVE) return;
        ChestBlockEntity chest = FrontierV3ExactItemPresentation.activeChest(level,
                new FrontierV3ExactItemPresentation.StoreTarget(target.position(), target.transfer().targetStoreId()));
        if (chest == null) { unknown(runtime, intent.id(), "fungible-arrival-chest-conflict"); return; }
        if (intent.status() == PhysicalIntentStatus.RUNNING) {
            if (confirmedFungibleArrival(state, target, chest)) confirmFungibleArrival(runtime, intent, target, chest);
            else unknown(runtime, intent.id(), "fungible-arrival-restart-postcondition-conflict");
            return;
        }
        if (!matchesFungibleReceiver(state, target, chest) || !canWriteFungibleArrival(state, target, chest)) {
            unknown(runtime, intent.id(), "fungible-arrival-precondition-conflict"); return;
        }
        if (!(transition(runtime, intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty(), "fungible-arrival-running") instanceof CommandResult.Accepted)) return;
        if (!writeFungibleArrival(chest, target)) { unknown(runtime, intent.id(), "fungible-arrival-physical-effect-conflict"); return; }
        if (!confirmedFungibleArrival(state, target, chest)) { unknown(runtime, intent.id(), "fungible-arrival-postcondition-conflict"); return; }
        confirmFungibleArrival(runtime, intent, target, chest);
    }

    private static boolean matchesFungibleReceiver(FrontierWorldState state, FungibleArrival target, ChestBlockEntity chest) {
        List<FungiblePhysicalObservation.Stack> observed = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, target.transfer().targetStoreId());
        if (target.receiver() == null) return observed.isEmpty();
        try {
            List<PhysicalStackBinding> expected = FungiblePhysicalObservation.bind(state.inventory().fungibleResources(), target.receiver().id(),
                    target.authorityEpoch(), observed);
            return new java.util.HashSet<>(expected).equals(new java.util.HashSet<>(state.inventory().fungibleResources().bindings().values().stream()
                    .filter(binding -> binding.accountId().equals(target.receiver().id())).toList()));
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private static boolean canWriteFungibleArrival(FrontierWorldState state, FungibleArrival target, ChestBlockEntity chest) {
        int slot = firstEmptySlot(chest); if (slot < 0) return false;
        ItemStack current = chest.getItem(slot); chest.setItem(slot, materializedFungible(target.lot()));
        boolean valid = confirmedFungibleArrival(state, target, chest); chest.setItem(slot, current); return valid;
    }

    private static boolean writeFungibleArrival(ChestBlockEntity chest, FungibleArrival target) {
        int slot = firstEmptySlot(chest); if (slot < 0) return false;
        chest.setItem(slot, materializedFungible(target.lot())); chest.setChanged(); return true;
    }

    private static int firstEmptySlot(ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).isEmpty()) return slot;
        return -1;
    }

    private static ItemStack materializedFungible(ResourceLot lot) {
        ResourceLocation id = ResourceLocation.tryParse(lot.itemKind());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(id), 64);
    }

    private static boolean confirmedFungibleArrival(FrontierWorldState state, FungibleArrival target, ChestBlockEntity chest) {
        List<FungiblePhysicalObservation.Stack> observed = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, target.transfer().targetStoreId());
        try {
            state.inventory().completeObservedFungibleCargoHandoff(target.transfer().cargoId(), target.transfer().targetStoreId(), target.authorityEpoch(), observed);
            return true;
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private static void confirmFungibleArrival(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent,
                                               FungibleArrival target, ChestBlockEntity chest) {
        FrontierWorldState current = runtime.decodedState().orElseThrow();
        List<FungiblePhysicalObservation.Stack> stacks = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, current, target.transfer().targetStoreId());
        PhysicalEffectObservation observation = new io.farfrontier.palemirror.frontier.v3.model.FungibleCargoHandoffObservation(
                new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(), target.transfer().cargoId(), target.authorityEpoch(), stacks);
        if (!(transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "fungible-arrival-confirmed") instanceof CommandResult.Accepted)) {
            throw new IllegalStateException("fungible hive nutrient arrival confirmation was rejected");
        }
    }

    private static Endpoint endpoint(FrontierWorldState state, PhysicalIntent intent) {
        HiveNutrientTransfer transfer = state.hiveColony().nutrientTransfers().get(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.TRANSFER));
        if (transfer == null || !transfer.endpointIntentId().equals(Optional.of(intent.id()))
                || !intent.roles().equals(intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE
                ? io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.nutrientDeparture(transfer.id(), transfer.cargoId(), transfer.itemId())
                : io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.nutrientArrival(transfer.id(), transfer.cargoId(), transfer.itemId()))) {
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
                : FrontierV3ExactItemPresentation.exactMatch(chest.getItem(target.slot()), target.item());
        if (observed) confirm(level, runtime, intent, target, chest); else unknown(runtime, intent.id(), "restart-postcondition-conflict");
    }

    static boolean matchesDeparture(ChestBlockEntity chest, int slot, ExactItemStack item) {
        return slot >= 0 && slot < chest.getContainerSize() && FrontierV3ExactItemPresentation.exactMatch(chest.getItem(slot), item);
    }
    static boolean matchesArrival(ChestBlockEntity chest, int slot) { return slot >= 0 && slot < chest.getContainerSize() && chest.getItem(slot).isEmpty(); }
    static boolean removeExact(ChestBlockEntity chest, int slot, ExactItemStack item) {
        if (!matchesDeparture(chest, slot, item)) return false;
        chest.setItem(slot, ItemStack.EMPTY); chest.setChanged(); return chest.getItem(slot).isEmpty();
    }
    static boolean insertExact(ChestBlockEntity chest, int slot, ExactItemStack item) {
        if (!matchesArrival(chest, slot)) return false;
        chest.setItem(slot, FrontierV3ExactItemPresentation.materializedStack(item)); chest.setChanged();
        return FrontierV3ExactItemPresentation.exactMatch(chest.getItem(slot), item);
    }

    private static void confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntent intent, Endpoint target, ChestBlockEntity chest) {
        PhysicalObservationId observationId = new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-'));
        PhysicalEffectObservation observation = intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE
                ? new HiveNutrientDepartureObservation(observationId, intent.id(), target.transfer().id(), target.transfer().cargoId(), target.item().id(), target.item().count())
                : new HiveNutrientArrivalObservation(observationId, intent.id(), target.transfer().id(), target.transfer().cargoId(), target.item().id(), target.item().count());
        CommandResult result = transition(runtime, intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(observation), "confirmed");
        if (!(result instanceof CommandResult.Accepted)) {
            throw new IllegalStateException("hive nutrient endpoint confirmation was rejected");
        }
        if (ReferenceContainerCustody.isReferenceContainer(runtime.decodedState().orElseThrow(), target.containerId())
                && !FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(runtime, target.containerId(), chest)) {
            throw new IllegalStateException("confirmed hive nutrient endpoint did not establish its next replica boundary");
        }
        String kind = intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE ? "hive_nutrient_departed" : "hive_nutrient_arrived";
        FrontierV3DiagnosticTrace.record(level.getServer(), FrontierV3DiagnosticTrace.hiveNutrientCorrelation(target.transfer().id()), kind, target.transfer().id(), result);
    }

    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId, String phase) {
        transition(runtime, intentId, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty(), phase);
    }

    private static CommandResult transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, PhysicalIntentId intentId,
                                      PhysicalIntentStatus status, Optional<PhysicalEffectObservation> observation, String phase) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId commandId = new CommandId("executor:hive-nutrient-" + phase + "-" + intentId.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), new PhysicalIntentTransition(intentId, status, observation)))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result;
    }

    private record Endpoint(HiveNutrientTransfer transfer, ContainerSurface surface, SubjectId containerId, int slot,
                            ExactItemStack item, BlockPos position) { }
    private record FungibleDeparture(HiveNutrientTransfer transfer, CustodyAccount account, ResourceLot lot,
                                    PhysicalStackBinding binding, long authorityEpoch, ContainerSurface surface,
                                    BlockPos position, int slot) { }
    private record FungibleArrival(HiveNutrientTransfer transfer, CustodyAccount cargo, ResourceLot lot, CustodyAccount receiver,
                                   long authorityEpoch, ContainerSurface surface, BlockPos position) { }
}
