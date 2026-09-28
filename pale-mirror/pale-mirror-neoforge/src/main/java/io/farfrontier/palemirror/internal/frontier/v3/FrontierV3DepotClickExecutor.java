package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.CompoundContainer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

/** Intercepts a server-authoritative vanilla chest click before and after its physical effect. */
final class FrontierV3DepotClickExecutor {
    enum Admission { NOT_OWNED, ACCEPTED, REJECTED }
    private static final Map<UUID, String> REPORTED = new LinkedHashMap<>();
    private FrontierV3DepotClickExecutor() { }

    static Admission before(ServerPlayer player, AbstractContainerMenu menu, int slot, int button, ClickType kind,
                            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (!(menu instanceof ChestMenu chestMenu)) return Admission.NOT_OWNED;
        ServerLevel level = player.serverLevel();
        FrontierWorldState state = runtime.decodedState().orElse(null);
        // Once v3 owns the physical world, an unavailable canonical image cannot authorize
        // an untracked edit to a potentially managed chest.
        if (state == null) return Admission.REJECTED;
        if (chestMenu.getContainer() instanceof CompoundContainer compound)
            return containsManagedDepot(level, state, compound) ? Admission.REJECTED : Admission.NOT_OWNED;
        if (!(chestMenu.getContainer() instanceof ChestBlockEntity chest)) return Admission.NOT_OWNED;
        var owned = ownedDepot(state, chest.getBlockPos());
        if (owned == null) return Admission.NOT_OWNED;
        SubjectId containerId = owned.id();
        var witnessLedger = FrontierV3DepotClickLedger.get(level);
        if (witnessLedger.pending(containerId) != null || kind == ClickType.SWAP
                || BakeryPhysicalAuthority.pendingForContainer(state, containerId)
                || FrontierV3ResourceSiteLedger.get(level).hasPendingFieldDelivery(containerId)
                || !exactSlotsCurrent(state, containerId, chest)) return Admission.REJECTED;
        if (slot >= 0 && slot < chest.getContainerSize()
                && state.inventory().itemAt(containerId, slot).isPresent()) return Admission.REJECTED;
        if (kind == ClickType.PICKUP_ALL && !menu.getCarried().isEmpty()
                && state.inventory().items().values().stream().anyMatch(item ->
                    item.custody() instanceof InventoryCustody.ContainerSlot source
                            && source.containerId().equals(containerId)
                            && menu.getCarried().is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.ResourceLocation.parse(item.itemKind()))))) return Admission.REJECTED;
        CustodyAccount account = state.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.custody().equals(new ResourceCustody.Container(containerId)))
                .findFirst().orElse(null);
        if (state.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.custody().equals(new ResourceCustody.Container(containerId))).count() > 1)
            return Admission.REJECTED;
        PhysicalCustodyLease lease = state.replicaCustody().custodyByScope()
                .get(ReferenceContainerCustody.scopeId(containerId));
        if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                || !ReferenceContainerCustody.hasOperationalCustody(state, containerId)
                || FrontierV3ContainerSurfaceExecutor.hasForeignFungibleComponents(chest, state, containerId))
            return Admission.REJECTED;
        var before = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, containerId);
        SubjectId accountId = account == null ? ReferenceContainerCustody.scopeId(containerId) : account.id();
        List<PhysicalStackBinding> canonical = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(accountId))
                .sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
        try {
            if (account == null ? !before.isEmpty() || !canonical.isEmpty()
                    : canonical.isEmpty() || !canonical.equals(FungiblePhysicalObservation.bind(
                        state.inventory().fungibleResources(), accountId, lease.authorityEpoch(), before)))
                return Admission.REJECTED;
        } catch (IllegalArgumentException invalid) { return Admission.REJECTED; }
        if (account != null && mayRemoveReserved(menu, slot, button, kind, canonical, account))
            return Admission.REJECTED;
        if (kind == ClickType.PICKUP && slot >= 0 && slot < chest.getContainerSize()) {
            ItemStack cursor = menu.getCarried(), source = chest.getItem(slot);
            if (!cursor.isEmpty() && !source.isEmpty() && !ItemStack.isSameItemSameComponents(cursor, source))
                return Admission.REJECTED;
        }
        var witness = new FrontierV3DepotClickWitness(containerId, accountId, owned.ownerId(),
                lease.authorityEpoch(), player.getUUID(), UUID.randomUUID(), before, java.util.Optional.empty());
        witnessLedger.prepare(witness);
        witnessLedger.persist(level);
        return Admission.ACCEPTED;
    }

    static boolean ownsDepot(ServerPlayer player, AbstractContainerMenu menu,
                             FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (!(menu instanceof ChestMenu chestMenu)) return false;
        return runtime.decodedState().map(state -> {
            if (chestMenu.getContainer() instanceof ChestBlockEntity chest)
                return ownedDepot(state, chest.getBlockPos()) != null;
            if (chestMenu.getContainer() instanceof CompoundContainer compound)
                return containsManagedDepot(player.serverLevel(), state, compound);
            return false;
        })
                .orElse(true);
    }

    private static boolean containsManagedDepot(ServerLevel level, FrontierWorldState state,
                                                CompoundContainer compound) {
        return state.inventory().surfaces().entrySet().stream()
                .filter(entry -> entry.getValue().status() == ContainerSurfaceStatus.ACTIVE)
                .filter(entry -> state.inventory().containers().get(entry.getKey()) instanceof ContainerRecord record
                        && record.ownerId().value().startsWith("settlement:")
                        && FrontierWorldState.depotId(record.ownerId()).equals(record.id()))
                .map(entry -> entry.getValue().position())
                .map(position -> new BlockPos(position.x(), position.y(), position.z()))
                .filter(level::hasChunkAt)
                .map(level::getBlockEntity)
                .anyMatch(entity -> entity instanceof ChestBlockEntity chest && compound.contains(chest));
    }

    static void after(ServerPlayer player, AbstractContainerMenu menu,
                      FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (!(menu instanceof ChestMenu chestMenu) || !(chestMenu.getContainer() instanceof ChestBlockEntity chest)) return;
        ServerLevel level = player.serverLevel();
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        ContainerRecord owned = ownedDepot(state, chest.getBlockPos());
        if (owned == null) return;
        FrontierV3DepotClickWitness witness = FrontierV3DepotClickLedger.get(level).pending(owned.id());
        if (witness == null || !witness.playerId().equals(player.getUUID())) return;
        reconcile(level, runtime, state, chest, witness);
    }

    /** Reconciles at most one pending witness before the ordinary uncaused layout poller runs. */
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state) {
        for (FrontierV3DepotClickWitness witness : FrontierV3DepotClickLedger.get(level).pending()) {
            ContainerSurface surface = state.inventory().surfaces().get(witness.containerId());
            if (surface == null) continue;
            BlockPos position = new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
            if (!level.hasChunkAt(position) || !(level.getBlockEntity(position) instanceof ChestBlockEntity chest)) continue;
            if (reconcile(level, runtime, state, chest, witness)) return true;
        }
        return false;
    }

    private static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  FrontierWorldState state, ChestBlockEntity chest,
                                  FrontierV3DepotClickWitness witness) {
        var ledger = FrontierV3DepotClickLedger.get(level);
        List<FungiblePhysicalObservation.Stack> physical = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(
                chest, state, witness.containerId());
        if (!exactSlotsCurrent(state, witness.containerId(), chest)
                || FrontierV3ContainerSurfaceExecutor.hasForeignFungibleComponents(chest, state, witness.containerId())) {
            conflict(runtime, witness, "foreign or changed exact chest components"); return false;
        }
        var current = state.inventory().fungibleResources();
        CustodyAccount account = current.accounts().get(witness.accountId());
        if (account == null && !witness.before().isEmpty() && witness.after().filter(List::isEmpty).isPresent()
                && physical.isEmpty()
                && current.accounts().values().stream().noneMatch(value ->
                    value.custody().equals(new ResourceCustody.Container(witness.containerId())))) {
            ledger.retire(witness); ledger.persist(level); REPORTED.remove(witness.interactionId()); return true;
        }
        if (account == null && !witness.before().isEmpty()) {
            conflict(runtime, witness, "source account disappeared before click commit"); return false;
        }
        // WAL may have committed just before a crash; the post-layout already equals canonical.
        if (account != null && witness.after().isPresent() && physical.equals(witness.after().orElseThrow())
                && canonicalMatches(current, account.id(), witness.authorityEpoch(), physical)) {
            if (!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(
                    runtime, witness.containerId(), chest)) return false;
            ledger.retire(witness); ledger.persist(level); REPORTED.remove(witness.interactionId()); return true;
        }
        if (physical.equals(witness.before()) && witness.after().isEmpty()) {
            ledger.retire(witness); ledger.persist(level); REPORTED.remove(witness.interactionId()); return true;
        }
        if (physical.equals(witness.before()) && witness.after().isPresent()
                && !witness.after().orElseThrow().equals(witness.before())) {
            conflict(runtime, witness, "post-click chest reverted after witnessed player action"); return false;
        }
        if (witness.after().isPresent() && !physical.equals(witness.after().orElseThrow())) {
            conflict(runtime, witness, "post-click chest diverged from durable witness"); return false;
        }
        // A click on a player slot, an empty slot, or a rejected vanilla transfer can leave
        // the managed chest unchanged. This is a completed physical no-op, including the
        // first click on an empty depot: it needs no account or synthetic layout event.
        if (physical.equals(witness.before())) {
            ledger.retire(witness); ledger.persist(level); REPORTED.remove(witness.interactionId());
            return true;
        }
        FrontierV3DepotClickWitness observed = witness;
        if (observed.after().isEmpty()) {
            observed = observed.observed(physical);
            ledger.observe(witness, observed); ledger.persist(level);
        }
        try {
            FrontierPayload payload = FungibleDepotPlayerEdit.classify(current, witness.accountId(), witness.containerId(),
                    witness.ownerId(), witness.authorityEpoch(), witness.playerId(), witness.interactionId(), physical);
            CommandResult result = FrontierV3CommandSubmission.submit(runtime, "depot-player-edit",
                    witness.interactionId().toString(), payload);
            if (result instanceof CommandResult.Accepted) {
                if (!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(
                        runtime, witness.containerId(), chest)) return false;
                ledger.retire(observed); ledger.persist(level); REPORTED.remove(observed.interactionId());
                return true;
            }
            conflict(runtime, observed, "canonical player edit rejected");
        } catch (IllegalArgumentException invalid) {
            conflict(runtime, observed, invalid.getMessage());
        }
        return false;
    }

    private static boolean canonicalMatches(FungibleResourceLedger resources, SubjectId account, long epoch,
                                            List<FungiblePhysicalObservation.Stack> physical) {
        try {
            List<PhysicalStackBinding> expected = FungiblePhysicalObservation.bind(resources, account, epoch, physical);
            List<PhysicalStackBinding> current = resources.bindings().values().stream()
                    .filter(binding -> binding.accountId().equals(account))
                    .sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
            return current.equals(expected);
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private static boolean mayRemoveReserved(AbstractContainerMenu menu, int slot, int button, ClickType kind,
                                             List<PhysicalStackBinding> bindings, CustodyAccount account) {
        if (account.claimQuantities().isEmpty()) return false;
        if (kind == ClickType.PICKUP_ALL) return true;
        if (slot < 0 || !(menu instanceof ChestMenu chestMenu) || slot >= chestMenu.getContainer().getContainerSize()) return false;
        if (kind != ClickType.PICKUP && kind != ClickType.QUICK_MOVE && kind != ClickType.THROW) return false;
        var matching = bindings.stream().filter(binding -> binding.address() instanceof PhysicalStackAddress.ContainerSlot source
                && source.slot().slot() == slot).findFirst().orElse(null);
        if (matching == null) return false;
        return exceedsUnclaimedPortion(matching, kind, button, menu.getCarried().isEmpty());
    }

    static boolean exceedsUnclaimedPortion(PhysicalStackBinding matching, ClickType kind,
                                           int button, boolean carriedEmpty) {
        int free = matching.quantity() - matching.claimQuantities().values().stream().mapToInt(Integer::intValue).sum();
        int possibleDeparture = switch (kind) {
            case QUICK_MOVE -> matching.quantity();
            case THROW -> button == 0 ? 1 : matching.quantity();
            case PICKUP -> carriedEmpty
                    ? button == 1 ? (matching.quantity() + 1) / 2 : matching.quantity() : 0;
            default -> 0;
        };
        return possibleDeparture > free;
    }

    private static ContainerRecord ownedDepot(FrontierWorldState state, BlockPos position) {
        return state.inventory().surfaces().entrySet().stream()
                .filter(entry -> entry.getValue().status() == ContainerSurfaceStatus.ACTIVE)
                .filter(entry -> entry.getValue().position().x() == position.getX()
                        && entry.getValue().position().y() == position.getY()
                        && entry.getValue().position().z() == position.getZ())
                .map(entry -> state.inventory().containers().get(entry.getKey()))
                .filter(value -> value != null && value.ownerId().value().startsWith("settlement:")
                        && FrontierWorldState.depotId(value.ownerId()).equals(value.id()))
                .findFirst().orElse(null);
    }

    private static boolean exactSlotsCurrent(FrontierWorldState state, SubjectId containerId,
                                             ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ExactItemStack exact = state.inventory().itemAt(containerId, slot).orElse(null);
            if (exact != null && !FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(slot), exact)) return false;
        }
        return true;
    }

    private static void conflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 FrontierV3DepotClickWitness witness, String reason) {
        if (REPORTED.size() >= 1_024) REPORTED.clear();
        if (!reason.equals(REPORTED.put(witness.interactionId(), reason)))
            PaleMirrorMod.LOGGER.warn("PMV3_DEPOT_CLICK unresolved container={} interaction={} reason={}",
                    witness.containerId(), witness.interactionId(), reason);
        // Keep the durable witness. The ordinary layout poller must not report a second,
        // uncaused conflict or adopt stock while this exact player transaction is unresolved.
    }
}
