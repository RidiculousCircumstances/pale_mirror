package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

/** Intercepts a server-authoritative vanilla managed-container click before and after its physical effect. */
final class FrontierV3ContainerClickExecutor {
    enum Admission { NOT_OWNED, ACCEPTED, REJECTED }
    private static final Map<UUID, String> REPORTED = new LinkedHashMap<>();
    private FrontierV3ContainerClickExecutor() { }

    static Admission before(ServerPlayer player, AbstractContainerMenu menu, int slot, int button, ClickType kind,
                            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (!FrontierV3ContainerMenuTarget.supported(menu)) return Admission.NOT_OWNED;
        ServerLevel level = player.serverLevel();
        FrontierWorldState state = runtime.decodedState().orElse(null);
        // Once v3 owns the physical world, an unavailable canonical image cannot authorize
        // an untracked edit to a potentially managed chest.
        if (state == null) return Admission.REJECTED;
        List<FrontierV3ContainerMenuTarget> targets;
        try { targets = FrontierV3ContainerMenuTarget.resolveAll(player, menu, state); }
        catch (IllegalArgumentException unavailable) { return Admission.REJECTED; }
        if (targets.isEmpty()) return Admission.NOT_OWNED;
        UUID interaction = UUID.randomUUID();
        var members = new java.util.ArrayList<FrontierV3DepotClickWitness>();
        for (var target : targets) {
            var witness = prepareMember(player, menu, slot, button, kind, state, target, interaction);
            if (witness == null) return Admission.REJECTED;
            members.add(witness);
        }
        var witnessLedger = FrontierV3DepotClickLedger.get(level);
        witnessLedger.prepare(new FrontierV3ContainerClickTransaction(members));
        witnessLedger.persist(level);
        return Admission.ACCEPTED;
    }

    private static FrontierV3DepotClickWitness prepareMember(ServerPlayer player, AbstractContainerMenu menu,
            int slot, int button, ClickType kind, FrontierWorldState state, FrontierV3ContainerMenuTarget target, UUID interaction) {
        ServerLevel level = player.serverLevel();
        var owned = target.record();
        var physical = target.physical();
        var chest = physical.inventory();
        int cargoSlot = target.cargoSlot(slot);
        ItemStack incoming = kind == ClickType.QUICK_MOVE && slot >= 0 && slot < menu.slots.size()
                && menu.getSlot(slot).container == player.getInventory() ? menu.getSlot(slot).getItem() : menu.getCarried();
        if (!FrontierV3ExactContainerIngress.admissible(state, player, incoming))
            return refused(player, target, slot, kind, "UNSUPPORTED_INCOMING_ITEM_COMPONENTS");
        if (target.equipmentSlot(slot)) return null;
        if (target.equipmentSlotCount() > 0 && kind == ClickType.QUICK_MOVE
                && slot >= target.firstCargoMenuSlot() + owned.slotCount() && slot < menu.slots.size()
                && (menu.getSlot(0).mayPlace(menu.getSlot(slot).getItem())
                    || menu.getSlot(1).mayPlace(menu.getSlot(slot).getItem()))) return null;
        SubjectId containerId = owned.id();
        var witnessLedger = FrontierV3DepotClickLedger.get(level);
        if (witnessLedger.pending(containerId) != null)
            return refused(player, target, slot, kind, "CONTAINER_CLICK_PENDING");
        if (witnessLedger.pending().stream().anyMatch(witness -> witness.playerId().equals(player.getUUID())))
            return refused(player, target, slot, kind, "PLAYER_CLICK_PENDING");
        if (kind == ClickType.SWAP) return refused(player, target, slot, kind, "SWAP_UNSUPPORTED");
        if (FrontierV3ContainerEffectFence.pending(level, state, containerId))
            return refused(player, target, slot, kind, "PHYSICAL_EFFECT_PENDING");
        if (!exactSlotsCurrent(state, containerId, chest))
            return refused(player, target, slot, kind, "EXACT_ITEM_LAYOUT_CHANGED");
        if (cargoSlot >= 0 && state.inventory().itemAt(containerId, cargoSlot).isPresent()) return null;
        if (kind == ClickType.PICKUP_ALL && !menu.getCarried().isEmpty()
                && state.inventory().items().values().stream().anyMatch(item ->
                    item.custody() instanceof InventoryCustody.ContainerSlot source
                            && source.containerId().equals(containerId)
                            && menu.getCarried().is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.ResourceLocation.parse(item.itemKind()))))) return null;
        CustodyAccount account = state.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.custody().equals(new ResourceCustody.Container(containerId)))
                .findFirst().orElse(null);
        if (state.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.custody().equals(new ResourceCustody.Container(containerId))).count() > 1)
            return null;
        PhysicalCustodyLease lease = state.replicaCustody().custodyByScope()
                .get(ReferenceContainerCustody.scopeId(containerId));
        if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED)
            return refused(player, target, slot, kind, "CUSTODY_NOT_ACQUIRED:" + (lease == null ? "MISSING" : lease.status()));
        if (!ReferenceContainerCustody.hasOperationalCustody(state, containerId))
            return refused(player, target, slot, kind, "CUSTODY_NOT_OPERATIONAL");
        if (FrontierV3ContainerSurfaceExecutor.hasForeignFungibleComponents(chest, state, containerId))
            return refused(player, target, slot, kind, "FOREIGN_ITEM_COMPONENTS");
        var before = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, containerId);
        SubjectId accountId = account == null ? ReferenceContainerCustody.scopeId(containerId) : account.id();
        List<PhysicalStackBinding> canonical = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(accountId))
                .sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
        try {
            if (account == null ? !before.isEmpty() || !canonical.isEmpty()
                    : canonical.isEmpty() || !canonical.equals(FungiblePhysicalObservation.bind(
                        state.inventory().fungibleResources(), accountId, lease.authorityEpoch(), before)))
                return refused(player, target, slot, kind, "FUNGIBLE_LAYOUT_MISMATCH");
        } catch (IllegalArgumentException invalid) { return refused(player, target, slot, kind, "INVALID_BINDING:" + invalid.getMessage()); }
        if (account != null && mayRemoveReserved(menu, cargoSlot, button, kind, canonical, account, state))
            return refused(player, target, slot, kind, "RESERVED_STOCK");
        if (kind == ClickType.PICKUP && cargoSlot >= 0) {
            ItemStack cursor = menu.getCarried(), source = chest.getItem(cargoSlot);
            if (!cursor.isEmpty() && !source.isEmpty() && !ItemStack.isSameItemSameComponents(cursor, source))
                return null;
        }
        java.util.Optional<FrontierV3DepotClickWitness.ReturnSource> returning;
        try { returning = playerReturnSource(player, menu, slot, kind, state); }
        catch (IllegalArgumentException invalid) { return refused(player, target, slot, kind, "INVALID_PLAYER_RETURN:" + invalid.getMessage()); }
        return new FrontierV3DepotClickWitness(containerId, accountId, owned.ownerId(),
                lease.authorityEpoch(), player.getUUID(), interaction, before, returning);
    }

    static boolean ownsContainer(ServerPlayer player, AbstractContainerMenu menu,
                             FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (!FrontierV3ContainerMenuTarget.supported(menu)) return false;
        return runtime.passiveOwnershipState().map(state -> {
            try { return FrontierV3ContainerMenuTarget.managedCompound(player, menu, state)
                    || !FrontierV3ContainerMenuTarget.resolveAll(player, menu, state).isEmpty(); }
            catch (IllegalArgumentException unavailable) { return true; }
        }).orElse(true);
    }

    /** One bounded receipt per refused ordinary click; no polling or retained diagnostic state. */
    private static FrontierV3DepotClickWitness refused(ServerPlayer player, FrontierV3ContainerMenuTarget target,
                                                       int slot, ClickType kind, String reason) {
        PaleMirrorMod.LOGGER.warn("PMV3_CONTAINER_CLICK_REJECTED container={} player={} slot={} kind={} reason={}",
                target.record().id(), player.getUUID(), slot, kind, reason);
        return null;
    }

    static void after(ServerPlayer player, AbstractContainerMenu menu,
                      FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        ServerLevel level = player.serverLevel();
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var targets = FrontierV3ContainerMenuTarget.resolveAll(player, menu, state);
        if (targets.isEmpty()) return;
        var target = targets.getFirst();
        var transaction = FrontierV3DepotClickLedger.get(level).transaction(target.record().id());
        if (transaction != null && transaction.members().getFirst().playerId().equals(player.getUUID()))
            FrontierV3CompositeClickExecutor.reconcile(level, runtime, state, transaction);
    }

    /** Reconciles at most one pending witness before the ordinary uncaused layout poller runs. */
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state) {
        for (var transaction : FrontierV3DepotClickLedger.get(level).transactions()) {
            if (FrontierV3CompositeClickExecutor.reconcile(level, runtime, state, transaction)) return true;
        }
        return false;
    }



    /** Only the clicked exact player slot supplies return authority; no inventory-wide stack matching. */
    private static java.util.Optional<FrontierV3DepotClickWitness.ReturnSource> playerReturnSource(ServerPlayer player,
            AbstractContainerMenu menu, int slot, ClickType kind, FrontierWorldState state) {
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.address() instanceof PhysicalStackAddress.PlayerSlot address
                        && address.playerId().equals(player.getUUID())).toList();
        var interacting = !menu.getCarried().isEmpty() ? menu.getCarried()
                : slot >= 0 && slot < menu.slots.size() ? menu.getSlot(slot).getItem() : ItemStack.EMPTY;
        String interactingKind = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(interacting.getItem()).toString();
        bindings = bindings.stream().filter(binding -> binding.itemKind().equals(interactingKind)).toList();
        if (bindings.isEmpty()) return java.util.Optional.empty();
        // A tracked player portion has an explicit supported return operation, never an implicit gift.
        if (kind != ClickType.QUICK_MOVE || !menu.getCarried().isEmpty())
            throw new IllegalArgumentException("tracked player stock requires a witnessed quick-move return");
        if (slot < 0 || slot >= menu.slots.size()) return java.util.Optional.empty();
        if (menu.getSlot(slot).container != player.getInventory()) {
            String incoming = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(menu.getSlot(slot).getItem().getItem()).toString();
            if (bindings.stream().anyMatch(binding -> binding.itemKind().equals(incoming)))
                throw new IllegalArgumentException("untracked withdrawal cannot merge into a tracked player portion");
            return java.util.Optional.empty();
        }
        int playerSlot = menu.getSlot(slot).getContainerSlot();
        var exact = bindings.stream().filter(binding -> ((PhysicalStackAddress.PlayerSlot) binding.address()).slot() == playerSlot).toList();
        if (exact.isEmpty()) return java.util.Optional.empty();
        if (exact.size() != 1) throw new IllegalArgumentException("ambiguous tracked player slot");
        var binding = exact.getFirst(); var stack = player.getInventory().getItem(playerSlot);
        if (!binding.claimQuantities().isEmpty() || stack.getCount() != binding.quantity()
                || !ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem(), stack.getCount()))
                || !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(binding.itemKind()))
            throw new IllegalArgumentException("tracked player source does not match its admitted layout");
        return java.util.Optional.of(new FrontierV3DepotClickWitness.ReturnSource(binding.id(), binding.accountId(),
                binding.authorityEpoch(), playerSlot, binding.itemKind(), binding.quantity()));
    }

    private static boolean mayRemoveReserved(AbstractContainerMenu menu, int slot, int button, ClickType kind,
                                             List<PhysicalStackBinding> bindings, CustodyAccount account,
                                             FrontierWorldState state) {
        if (account.claimQuantities().isEmpty()) return false;
        if (kind == ClickType.PICKUP_ALL) {
            var carried = menu.getCarried();
            if (carried.isEmpty()) return false;
            String itemKind = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
            int available = bindings.stream().filter(binding -> binding.itemKind().equals(itemKind))
                    .mapToInt(PhysicalStackBinding::quantity).sum();
            int departure = Math.min(available, carried.getMaxStackSize() - carried.getCount());
            return !SourceStockClaimLoss.canDepart(state, account.id(), itemKind, departure);
        }
        if (slot < 0) return false;
        if (kind != ClickType.PICKUP && kind != ClickType.QUICK_MOVE && kind != ClickType.THROW) return false;
        var matching = bindings.stream().filter(binding -> binding.address() instanceof PhysicalStackAddress.ContainerSlot source
                && source.slot().slot() == slot).findFirst().orElse(null);
        if (matching == null) return false;
        int possibleDeparture = switch (kind) {
            case QUICK_MOVE -> matching.quantity();
            case THROW -> button == 0 ? 1 : matching.quantity();
            case PICKUP -> menu.getCarried().isEmpty()
                    ? button == 1 ? (matching.quantity() + 1) / 2 : matching.quantity() : 0;
            default -> 0;
        };
        return !SourceStockClaimLoss.canDepart(state, account.id(), matching.itemKind(), possibleDeparture);
    }

    static ContainerRecord ownedContainer(FrontierWorldState state, BlockPos position) {
        return state.inventory().surfaces().entrySet().stream()
                .filter(entry -> entry.getValue().fixed())
                .filter(entry -> entry.getValue().status() != ContainerSurfaceStatus.RETIRED)
                .filter(entry -> entry.getValue().position().x() == position.getX()
                        && entry.getValue().position().y() == position.getY()
                        && entry.getValue().position().z() == position.getZ())
                .map(entry -> state.inventory().containers().get(entry.getKey()))
                .filter(value -> value != null && ReferenceContainerCustody.isReferenceContainer(state, value.id()))
                .findFirst().orElse(null);
    }

    static boolean exactSlotsCurrent(FrontierWorldState state, SubjectId containerId,
                                             net.minecraft.world.Container chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ExactItemStack exact = state.inventory().itemAt(containerId, slot).orElse(null);
            if (exact != null && !FrontierV3ExactItemPresentation.exactMatch(chest.getItem(slot), exact)) return false;
        }
        return true;
    }

    static void conflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                 FrontierV3DepotClickWitness witness, String reason) {
        if (REPORTED.size() >= 1_024) REPORTED.clear();
        if (!reason.equals(REPORTED.put(witness.interactionId(), reason)))
            PaleMirrorMod.LOGGER.warn("PMV3_DEPOT_CLICK unresolved container={} interaction={} reason={}",
                    witness.containerId(), witness.interactionId(), reason);
        // Keep the durable witness. The ordinary layout poller must not report a second,
        // uncaused conflict or adopt stock while this exact player transaction is unresolved.
    }
}
