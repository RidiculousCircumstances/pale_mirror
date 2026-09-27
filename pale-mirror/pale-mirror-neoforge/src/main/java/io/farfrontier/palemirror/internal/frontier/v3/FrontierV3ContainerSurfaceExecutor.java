package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceActivationStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceTransition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.FrontierContainerSocketPlan;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.ProductionTransformationStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Materializes one exact inventory surface at a time in naturally loaded chunks.
 *
 * <p>This executor owns the physical chest lifecycle, while operation executors only use an
 * already {@linkplain ContainerSurfaceStatus#ACTIVE active} container. Once preparation was
 * durably recorded, recovery inspects the owned chest against the canonical slots; it never
 * reconstructs a missing or altered surface.</p>
 */
final class FrontierV3ContainerSurfaceExecutor {
    enum SocketReadiness { DEFERRED, READY, CONFLICT }

    /**
     * Read-only loaded-world facts for one exact container socket.  It deliberately exposes no
     * mutable inventory stack and never loads a chunk: operators need to distinguish an absent
     * projection, a foreign socket and a stale owned chest without guessing from CONFLICT.
     */
    record Readiness(String chunk, String freshSocket, String support, String targetBlock, String chest, String slots, String mismatch,
                     boolean ordinaryPlayerNearby, boolean presentationDemand, int eligibleObserverCount, int presentationObserverCount) { }

    private FrontierV3ContainerSurfaceExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        state.inventory().surfaces().values().stream()
                .filter(surface -> surface.status() == ContainerSurfaceStatus.UNMATERIALIZED
                        || surface.status() == ContainerSurfaceStatus.PREPARED)
                .sorted(Comparator.comparing(ContainerSurface::containerId))
                .filter(surface -> level.hasChunkAt(position(surface)))
                .findFirst().ifPresent(surface -> executeLifecycle(level, runtime, state, surface));
        auditOneActiveSurface(level, runtime, state);
    }

    private static void executeLifecycle(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         FrontierWorldState state, ContainerSurface surface) {
        // Reference preparation transfers held input and establishes exclusive before-write
        // custody atomically. Other surface families retain their existing admission guard.
        if (!ReferenceContainerCustody.isReferenceContainer(state, surface.containerId())
                && ContainerSurfaceActivationStateSupport.blockedByColdProduction(state, surface.containerId())) return;
        BlockPos target = position(surface);
        if (surface.status() == ContainerSurfaceStatus.UNMATERIALIZED) {
            SocketReadiness readiness = socketReadiness(level, FrontierV3GrayboxLedger.get(level), target,
                    FrontierContainerSocketPlan.support(state, surface).orElse(null));
            if (readiness == SocketReadiness.DEFERRED) return;
            if (readiness == SocketReadiness.CONFLICT) {
                transition(runtime, surface.containerId(), ContainerSurfaceStatus.CONFLICT);
                return;
            }
            if (ReferenceContainerCustody.isReferenceContainer(state, surface.containerId())) {
                if (!FrontierV3ReferenceContainerCustodyExecutor.prepareInitialProjection(runtime, state, surface.containerId())) return;
                state = state(runtime);
                if (state == null) return;
            } else if (!transition(runtime, surface.containerId(), ContainerSurfaceStatus.PREPARED)) return;
            ChestBlockEntity chest = claimFreshChest(level, target, surface.containerId());
            if (chest == null || !writeCanonicalSlots(chest, state, surface.containerId())) {
                transition(runtime, surface.containerId(), ContainerSurfaceStatus.CONFLICT);
                return;
            }
            transition(runtime, surface.containerId(), ContainerSurfaceStatus.ACTIVE);
            return;
        }
        // PREPARED can survive a restart both before and after the physical chest write.  Its
        // support may still be one materializer turn behind, so wait for an absent owned
        // foundation; then either claim an empty socket or inspect the already-owned chest.
        SocketReadiness readiness = supportReadiness(level, FrontierV3GrayboxLedger.get(level), target,
                FrontierContainerSocketPlan.support(state, surface).orElse(null));
        if (readiness == SocketReadiness.DEFERRED) return;
        if (readiness == SocketReadiness.CONFLICT) {
            reportConflict(runtime, surface.containerId());
            return;
        }
        ChestBlockEntity chest = activeChest(level, target, surface.containerId());
        if (chest != null) {
            // PREPARED is durable before the initial slot write.  An owned empty chest is the
            // exact pre-write recovery state; populate it once.  A nonempty mismatch remains
            // player/world evidence and is never overwritten.
            if (chest.isEmpty()) {
                if (!restorePreparedOwnedEmptyChest(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
                else transition(runtime, surface.containerId(), ContainerSurfaceStatus.ACTIVE);
            } else if (!matchesCanonicalSlotsOrPendingProductionOutput(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
            else transition(runtime, surface.containerId(), ContainerSurfaceStatus.ACTIVE);
            return;
        }
        chest = claimFreshChest(level, target, surface.containerId());
        if (chest == null || !writeCanonicalSlots(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
        else transition(runtime, surface.containerId(), ContainerSurfaceStatus.ACTIVE);
    }

    /** Bounded fair drift inspection for retained active surfaces; it does not mutate blocks. */
    private static void auditOneActiveSurface(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                              FrontierWorldState state) {
        var active = state.inventory().surfaces().values().stream()
                .filter(surface -> surface.status() == ContainerSurfaceStatus.ACTIVE)
                .filter(surface -> !ReferenceContainerCustody.isReferenceContainer(state, surface.containerId()))
                .sorted(Comparator.comparing(ContainerSurface::containerId)).toList();
        if (active.isEmpty()) return;
        ContainerSurface surface = active.get((int) Math.floorMod(level.getGameTime(), active.size()));
        if (!level.hasChunkAt(position(surface))) return;
        if (!hasReadySocket(level, FrontierV3GrayboxLedger.get(level), position(surface),
                FrontierContainerSocketPlan.support(state, surface).orElse(null))) {
            reportConflict(runtime, surface.containerId());
            return;
        }
        ChestBlockEntity chest = activeChest(level, position(surface), surface.containerId());
        if (chest == null || !matchesCanonicalSlotsOrPendingProductionOutput(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
    }

    /** Creates a fresh owned chest only after durable PREPARED state exists. */
    static ChestBlockEntity claimFreshChest(ServerLevel level, BlockPos target, SubjectId containerId) {
        if (!level.getBlockState(target).isAir() || level.getBlockState(target.below()).isAir()) return null;
        if (!level.setBlock(target, Blocks.CHEST.defaultBlockState(), 3)) return null;
        if (!(level.getBlockEntity(target) instanceof ChestBlockEntity chest)) return null;
        if (!chest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY).isBlank() || !chest.isEmpty()) return null;
        chest.getPersistentData().putString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY, containerId.value());
        chest.getPersistentData().putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY,
                ReferenceContainerCustody.provenance(containerId));
        chest.setChanged();
        return chest;
    }

    static ChestBlockEntity activeChest(ServerLevel level, BlockPos target, SubjectId containerId) {
        if (!(level.getBlockEntity(target) instanceof ChestBlockEntity chest)) return null;
        return containerId.value().equals(chest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY)) ? chest : null;
    }

    static boolean writeCanonicalSlots(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        if (!chest.isEmpty()) return false;
        Optional<List<ItemStack>> planned = plannedCanonicalSlots(state, containerId, chest.getContainerSize());
        if (planned.isEmpty()) return false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, planned.orElseThrow().get(slot));
        chest.setChanged();
        return matchesCanonicalSlots(chest, state, containerId);
    }

    /** The sole safe PREPARED recovery write: an already-owned chest must still be empty. */
    static boolean restorePreparedOwnedEmptyChest(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        return chest.isEmpty() && writeCanonicalSlots(chest, state, containerId);
    }

    /**
     * Replaces an already-owned exact chest only after the replica adapter has compared the
     * complete old physical snapshot to retained evidence and durably emitted the next one.
     * It is deliberately package-private so no generic surface lifecycle can use it.
     */
    static boolean replaceCanonicalSlots(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        Optional<List<ItemStack>> planned = plannedCanonicalSlots(state, containerId, chest.getContainerSize());
        if (planned.isEmpty()) return false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, planned.orElseThrow().get(slot));
        chest.setChanged();
        return matchesCanonicalSlots(chest, state, containerId);
    }

    static boolean matchesCanonicalSlots(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        if (state.inventory().containers().get(containerId) == null) return false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ExactItemStack expected = state.inventory().itemAt(containerId, slot).orElse(null);
            if (expected != null && !FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(slot), expected)) return false;
        }
        return matchesFungibleSlots(chest, state, containerId);
    }

    /**
     * A physical transformation has a deliberately tiny crash window: its exact tagged output
     * can be durable in one named owned slot before the corresponding canonical receipt is
     * replayed.  That is not player drift.  Accept only this exact pending output; every other
     * slot (and every approximate/foreign output) remains strict conflict evidence.
     */
    static boolean matchesCanonicalSlotsOrPendingProductionOutput(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        if (matchesCanonicalSlots(chest, state, containerId)) return true;
        if (state.inventory().containers().get(containerId) == null) return false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ExactItemStack expected = state.inventory().itemAt(containerId, slot).orElse(null);
            if (expected == null ? chest.getItem(slot).isEmpty()
                    : FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(slot), expected)) continue;
            if (!pendingProductionOutputAt(state, containerId, slot, chest.getItem(slot))) return false;
        }
        return true;
    }

    static List<FungiblePhysicalObservation.Stack> observedFungibleSlots(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        List<FungiblePhysicalObservation.Stack> observed = new java.util.ArrayList<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (state.inventory().itemAt(containerId, slot).isPresent()) continue;
            ItemStack actual = chest.getItem(slot);
            if (!actual.isEmpty()) observed.add(new FungiblePhysicalObservation.Stack(
                    new PhysicalStackAddress.ContainerSlot(new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot(containerId, slot)),
                    BuiltInRegistries.ITEM.getKey(actual.getItem()).toString(), actual.getCount()));
        }
        return List.copyOf(observed);
    }

    /** Fungible layout bytes carry kind/count only; modified item components cannot be erased into that evidence. */
    static boolean hasForeignFungibleComponents(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (state.inventory().itemAt(containerId, slot).isPresent()) continue;
            ItemStack actual = chest.getItem(slot);
            if (!actual.isEmpty() && !ItemStack.isSameItemSameComponents(actual,
                    new ItemStack(actual.getItem(), actual.getCount()))) return true;
        }
        return false;
    }

    private static boolean matchesFungibleSlots(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        if (hasForeignFungibleComponents(chest, state, containerId)) return false;
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        List<io.farfrontier.palemirror.frontier.v3.model.CustodyAccount> accounts = resources.accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container value && value.containerId().equals(containerId)).toList();
        if (accounts.size() > 1) return false;
        List<FungiblePhysicalObservation.Stack> observed = observedFungibleSlots(chest, state, containerId);
        if (overlapsHarvestOutputReservation(state, observed)) return false;
        if (accounts.isEmpty()) return observed.isEmpty();
        if (state.inventory().containers().get(containerId).productionStation().isPresent()) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                var expectedSlot = ReferenceContainerCustody.expectedFungibleSlot(state, containerId, slot).orElse(null);
                ItemStack actual = chest.getItem(slot);
                if (expectedSlot == null) {
                    if (state.inventory().itemAt(containerId, slot).isEmpty() && !actual.isEmpty()) return false;
                } else if (!actual.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(expectedSlot.itemKind())))
                        || actual.getCount() != expectedSlot.quantity()) return false;
            }
        }
        long epoch = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(accounts.getFirst().id()))
                .mapToLong(io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding::authorityEpoch).findFirst().orElse(1L);
        try {
            FungiblePhysicalObservation.bind(resources, accounts.getFirst().id(), epoch, observed);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** Build the complete owned image before touching even one physical slot. */
    static Optional<List<ItemStack>> plannedCanonicalSlots(FrontierWorldState state, SubjectId containerId, int capacity) {
        if (state.inventory().containers().get(containerId) == null
                || capacity != state.inventory().containers().get(containerId).slotCount()) return Optional.empty();
        List<ItemStack> planned = new ArrayList<>(java.util.Collections.nCopies(capacity, ItemStack.EMPTY));
        for (int slot = 0; slot < capacity; slot++) {
            ExactItemStack item = state.inventory().itemAt(containerId, slot).orElse(null);
            if (item != null) {
                ResourceLocation id = ResourceLocation.tryParse(item.itemKind());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)
                        || BuiltInRegistries.ITEM.get(id) == net.minecraft.world.item.Items.AIR
                        || item.count() > BuiltInRegistries.ITEM.get(id).getDefaultMaxStackSize()) return Optional.empty();
                planned.set(slot, FrontierV3CargoHandoffExecutor.materializedStack(item));
            }
        }
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        List<io.farfrontier.palemirror.frontier.v3.model.CustodyAccount> accounts = resources.accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.Container value && value.containerId().equals(containerId)).toList();
        if (accounts.size() > 1) return Optional.empty();
        if (accounts.isEmpty()) return Optional.of(List.copyOf(planned));
        if (state.inventory().containers().get(containerId).productionStation().isPresent()) {
            for (int slot = 0; slot < capacity; slot++) {
                var expected = ReferenceContainerCustody.expectedFungibleSlot(state, containerId, slot).orElse(null);
                if (expected == null) continue;
                ResourceLocation id = ResourceLocation.tryParse(expected.itemKind());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)
                        || BuiltInRegistries.ITEM.get(id) == net.minecraft.world.item.Items.AIR
                        || !planned.get(slot).isEmpty()
                        || expected.quantity() > BuiltInRegistries.ITEM.get(id).getDefaultMaxStackSize()) return Optional.empty();
                planned.set(slot, new ItemStack(BuiltInRegistries.ITEM.get(id), expected.quantity()));
            }
            return Optional.of(List.copyOf(planned));
        }
        Map<String, Long> quantities = new LinkedHashMap<>();
        accounts.getFirst().lotQuantities().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String kind = resources.lots().get(entry.getKey()).itemKind();
            quantities.merge(kind, entry.getValue().longValue(), Math::addExact);
        });
        int slot = 0;
        for (Map.Entry<String, Long> entry : quantities.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)
                    || BuiltInRegistries.ITEM.get(id) == net.minecraft.world.item.Items.AIR) return Optional.empty();
            long remaining = entry.getValue();
            while (remaining > 0) {
                // A retained field job owns its future exact output slot even while its
                // wheat is still carried by the farmer. The fungible cold-stock image must
                // leave that slot vacant, or first visibility creates a physical collision
                // which only surfaces at the much later terminal harvest receipt.
                slot = nextProjectionSlot(state, containerId, capacity, slot, index -> !planned.get(index).isEmpty());
                if (slot == capacity) return Optional.empty();
                var item = BuiltInRegistries.ITEM.get(id);
                int count = (int) Math.min(item.getDefaultMaxStackSize(), remaining);
                if (count < 1) return Optional.empty();
                planned.set(slot++, new ItemStack(item, count));
                remaining -= count;
            }
        }
        return Optional.of(List.copyOf(planned));
    }

    /** Shared by the complete-image writer and its pure slot-selection regression. */
    static int nextProjectionSlot(FrontierWorldState state, SubjectId containerId, int capacity, int from,
                                  java.util.function.IntPredicate occupied) {
        for (int candidate = from; candidate < capacity; candidate++) {
            if (!occupied.test(candidate)
                    && !state.harvestOutputReserves(new InventoryCustody.ContainerSlot(containerId, candidate))) return candidate;
        }
        return capacity;
    }

    /** A physically occupied promised output slot is drift, never available fungible custody. */
    static boolean overlapsHarvestOutputReservation(FrontierWorldState state, List<FungiblePhysicalObservation.Stack> stacks) {
        return stacks.stream().anyMatch(stack -> stack.address() instanceof PhysicalStackAddress.ContainerSlot address
                && state.harvestOutputReserves(address.slot()));
    }

    private static boolean pendingProductionOutputAt(FrontierWorldState state, SubjectId containerId, int slot, net.minecraft.world.item.ItemStack physical) {
        return state.physicalIntents().values().stream()
                .filter(intent -> intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                .anyMatch(intent -> matchesPendingProductionOutput(state, intent, containerId, slot, physical));
    }

    private static boolean matchesPendingProductionOutput(FrontierWorldState state, PhysicalIntent intent, SubjectId containerId, int slot,
                                                          net.minecraft.world.item.ItemStack physical) {
        try {
            ProductionTransformationStateSupport.Target target = ProductionTransformationStateSupport.target(state, intent);
            return target.slot().containerId().equals(containerId) && target.slot().slot() == slot
                    && FrontierV3CargoHandoffExecutor.exactMatch(physical, target.output());
        } catch (IllegalArgumentException ignored) {
            // A broken canonical intent never grants a physical exception to the drift audit.
            return false;
        }
    }

    static boolean reportConflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId containerId) {
        return transition(runtime, containerId, ContainerSurfaceStatus.CONFLICT);
    }

    static Readiness readiness(ServerLevel level, FrontierWorldState state, SubjectId containerId) {
        ContainerSurface surface = state.inventory().surfaces().get(containerId);
        if (surface == null) return new Readiness("UNKNOWN_CONTAINER", "", "", "", "", "", "", false, false, 0, 0);
        BlockPos target = position(surface);
        // For the F0.2B reference scopes an evicted-ticking chunk retained in the server's
        // serialization cache is prior replica evidence, not a physical custody candidate.
        // Keep the generic materializer's loaded-socket diagnostic unchanged for every other
        // family while reporting the same eligibility boundary used by the custody adapter.
        if (!level.hasChunkAt(target) || (ReferenceContainerCustody.isReferenceContainer(state, containerId) && !level.shouldTickBlocksAt(target))) {
            return new Readiness("UNLOADED", "", "", "", "", "", "", false, false, 0, 0);
        }
        GrayboxCell support = FrontierContainerSocketPlan.support(state, surface).orElse(null);
        SocketReadiness supportStatus = supportReadiness(level, FrontierV3GrayboxLedger.get(level), target, support);
        ChestBlockEntity owned = activeChest(level, target, containerId);
        ChestBlockEntity chest = level.getBlockEntity(target) instanceof ChestBlockEntity value ? value : null;
        SocketReadiness fresh = socketReadiness(level, FrontierV3GrayboxLedger.get(level), target, support);
        String chestStatus = chest == null ? "NO_CHEST" : owned != null ? "OWNED" : "FOREIGN_OR_UNTAGGED";
        String slots = owned == null ? "UNAVAILABLE" : matchesCanonicalSlots(owned, state, containerId) ? "CURRENT"
                : matchesCanonicalSlotsOrPendingProductionOutput(owned, state, containerId) ? "PENDING_PRODUCTION_OUTPUT" : "MISMATCH";
        String mismatch = owned == null || slots.equals("CURRENT") || slots.equals("PENDING_PRODUCTION_OUTPUT") ? ""
                : firstMismatch(owned, state, containerId);
        FrontierV3PhysicalDemand.Readiness demand = FrontierV3PhysicalDemand.readiness(level, target);
        return new Readiness("LOADED", freshSocketDiagnostic(fresh, owned != null), supportStatus.name(),
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(target).getBlock()).toString(), chestStatus, slots, mismatch,
                demand.ordinaryPlayerNearby(), demand.presentationDemand(), demand.eligibleObserverCount(), demand.presentationObserverCount());
    }

    /** A fresh-socket probe is intentionally inapplicable after the exact owned chest exists. */
    static String freshSocketDiagnostic(SocketReadiness fresh, boolean ownedChestPresent) {
        return ownedChestPresent ? "NOT_APPLICABLE_OWNED" : fresh.name();
    }

    private static String firstMismatch(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ExactItemStack expected = state.inventory().itemAt(containerId, slot).orElse(null); ItemStack actual = chest.getItem(slot);
            if (expected == null ? actual.isEmpty() : FrontierV3CargoHandoffExecutor.exactMatch(actual, expected)) continue;
            CustomData data = actual.get(DataComponents.CUSTOM_DATA);
            String actualId = data == null ? "" : data.copyTag().getString(FrontierV3CargoHandoffExecutor.ITEM_ID_KEY);
            return "slot=" + slot + ";expected=" + (expected == null ? "EMPTY" : expected.id().value() + "/" + expected.itemKind() + "/" + expected.count())
                    + ";actual=" + (actual.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(actual.getItem()) + "/" + actual.getCount() + "/" + actualId);
        }
        return "";
    }

    /**
     * Separates a not-yet-projected owned socket from a player/world obstruction.  Only the
     * latter is terminal conflict evidence; the former must wait without changing canonical
     * container state.
     */
    static SocketReadiness socketReadiness(ServerLevel level, FrontierV3GrayboxLedger ledger, BlockPos target, GrayboxCell support) {
        // A destroyed/temporarily unavailable canonical facility has no socket to materialize;
        // it is capacity loss, not evidence that the player obstructed a future chest.
        if (!level.getBlockState(target).isAir()) return SocketReadiness.CONFLICT;
        return supportReadiness(level, ledger, target, support);
    }

    /** Checks only the owned foundation, so PREPARED recovery can inspect an already-owned chest. */
    static SocketReadiness supportReadiness(ServerLevel level, FrontierV3GrayboxLedger ledger, BlockPos target, GrayboxCell support) {
        if (support == null) return SocketReadiness.DEFERRED;
        BlockPos supportPosition = target.below();
        FrontierV3GrayboxLedger.Claim claim = ledger.claim(supportPosition);
        if (claim == null && level.getBlockState(supportPosition).isAir()) return SocketReadiness.DEFERRED;
        return matchesReadySocket(level, claim, support, supportPosition) ? SocketReadiness.READY : SocketReadiness.CONFLICT;
    }

    private static boolean hasReadySocket(ServerLevel level, FrontierV3GrayboxLedger ledger, BlockPos target, GrayboxCell support) {
        return support != null && matchesReadySocket(level, ledger.claim(target.below()), support, target.below());
    }

    private static boolean matchesReadySocket(ServerLevel level, FrontierV3GrayboxLedger.Claim claim, GrayboxCell support, BlockPos position) {
        return claim != null && !claim.conflicted() && claim.owner().equals(support.ownerId().value())
                && claim.material().equals(support.material().name()) && claim.semanticPart().equals(support.semanticPart().name())
                && level.getBlockState(position).equals(FrontierV3GrayboxExecutor.material(support.material()));
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId containerId,
                                      ContainerSurfaceStatus status) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId commandId = new CommandId("executor:surface-" + status.name().toLowerCase(java.util.Locale.ROOT)
                + "-" + containerId.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId),
                new ContainerSurfaceTransition(containerId, status))).orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }

    private static BlockPos position(ContainerSurface surface) {
        return new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.decodedState().orElse(null);
    }
}
