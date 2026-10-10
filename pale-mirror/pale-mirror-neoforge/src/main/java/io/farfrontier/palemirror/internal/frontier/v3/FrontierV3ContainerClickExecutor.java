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
        if (FrontierV3ContainerMenuTarget.managedCompound(player, menu, state)) return Admission.REJECTED;
        FrontierV3ContainerMenuTarget target;
        try { target = FrontierV3ContainerMenuTarget.resolve(player, menu, state); }
        catch (IllegalArgumentException unavailable) { return Admission.REJECTED; }
        if (target == null) return Admission.NOT_OWNED;
        var owned = target.record();
        var physical = target.physical();
        var chest = physical.inventory();
        int cargoSlot = target.cargoSlot(slot);
        if (target.equipmentSlot(slot)) return Admission.REJECTED;
        if (target.firstCargoMenuSlot() > 0 && kind == ClickType.QUICK_MOVE
                && slot >= target.firstCargoMenuSlot() + owned.slotCount() && slot < menu.slots.size()
                && (menu.getSlot(0).mayPlace(menu.getSlot(slot).getItem())
                    || menu.getSlot(1).mayPlace(menu.getSlot(slot).getItem()))) return Admission.REJECTED;
        SubjectId containerId = owned.id();
        var witnessLedger = FrontierV3DepotClickLedger.get(level);
        if (witnessLedger.pending(containerId) != null || witnessLedger.pending().stream().anyMatch(witness ->
                witness.playerId().equals(player.getUUID())) || kind == ClickType.SWAP
                || FrontierV3ContainerEffectFence.pending(level, state, containerId)
                || !exactSlotsCurrent(state, containerId, chest)) return Admission.REJECTED;
        if (cargoSlot >= 0 && state.inventory().itemAt(containerId, cargoSlot).isPresent()) return Admission.REJECTED;
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
        if (account != null && mayRemoveReserved(menu, cargoSlot, button, kind, canonical, account, state))
            return Admission.REJECTED;
        if (kind == ClickType.PICKUP && cargoSlot >= 0) {
            ItemStack cursor = menu.getCarried(), source = chest.getItem(cargoSlot);
            if (!cursor.isEmpty() && !source.isEmpty() && !ItemStack.isSameItemSameComponents(cursor, source))
                return Admission.REJECTED;
        }
        java.util.Optional<FrontierV3DepotClickWitness.ReturnSource> returning;
        try { returning = playerReturnSource(player, menu, slot, kind, state); }
        catch (IllegalArgumentException invalid) { return Admission.REJECTED; }
        var witness = new FrontierV3DepotClickWitness(containerId, accountId, owned.ownerId(),
                lease.authorityEpoch(), player.getUUID(), UUID.randomUUID(), before, java.util.Optional.empty(),
                returning, java.util.Optional.empty());
        witnessLedger.prepare(witness);
        witnessLedger.persist(level);
        return Admission.ACCEPTED;
    }

    static boolean ownsContainer(ServerPlayer player, AbstractContainerMenu menu,
                             FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (!FrontierV3ContainerMenuTarget.supported(menu)) return false;
        return runtime.passiveOwnershipState().map(state -> {
            try { return FrontierV3ContainerMenuTarget.managedCompound(player, menu, state)
                    || FrontierV3ContainerMenuTarget.resolve(player, menu, state) != null; }
            catch (IllegalArgumentException unavailable) { return true; }
        }).orElse(true);
    }

    static void after(ServerPlayer player, AbstractContainerMenu menu,
                      FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        ServerLevel level = player.serverLevel();
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var target = FrontierV3ContainerMenuTarget.resolve(player, menu, state);
        if (target == null) return;
        ContainerRecord owned = target.record();
        FrontierV3DepotClickWitness witness = FrontierV3DepotClickLedger.get(level).pending(owned.id());
        if (witness == null || !witness.playerId().equals(player.getUUID())) return;
        reconcile(level, runtime, state, target.physical(), witness);
    }

    /** Reconciles at most one pending witness before the ordinary uncaused layout poller runs. */
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                FrontierWorldState state) {
        for (FrontierV3DepotClickWitness witness : FrontierV3DepotClickLedger.get(level).pending()) {
            ContainerSurface surface = state.inventory().surfaces().get(witness.containerId());
            if (surface == null) continue;
            var physical = FrontierV3PhysicalContainer.loaded(level, state, witness.containerId()).orElse(null);
            if (physical == null) continue;
            if (reconcile(level, runtime, state, physical, witness)) return true;
        }
        return false;
    }

    private static boolean reconcile(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                  FrontierWorldState state, FrontierV3PhysicalContainer physicalContainer,
                                  FrontierV3DepotClickWitness witness) {
        var chest = physicalContainer.inventory();
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
            if (!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedContainerMutation(
                    runtime, witness.containerId(), physicalContainer)) return false;
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
            java.util.Optional<Integer> remaining = java.util.Optional.empty();
            if (witness.returnSource().isPresent()) {
                var player = level.getServer().getPlayerList().getPlayer(witness.playerId());
                if (player == null) return false; // Never guess a missing player's postimage.
                var source = witness.returnSource().orElseThrow();
                var actual = player.getInventory().getItem(source.playerSlot());
                if (!actual.isEmpty() && !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(actual.getItem())
                        .toString().equals(source.itemKind())) {
                    conflict(runtime, witness, "player return source changed to a foreign kind"); return false;
                }
                remaining = java.util.Optional.of(actual.isEmpty() ? 0 : actual.getCount());
            }
            observed = observed.observed(physical, remaining);
            ledger.observe(witness, observed); ledger.persist(level);
        }
        try {
            FrontierPayload payload;
            if (observed.returnSource().isPresent()) {
                var source = observed.returnSource().orElseThrow();
                var binding = current.bindings().get(source.bindingId());
                if (binding == null || !binding.accountId().equals(source.accountId()) || binding.authorityEpoch() != source.epoch()
                        || binding.quantity() != source.quantity() || !binding.itemKind().equals(source.itemKind())
                        || !binding.address().equals(new PhysicalStackAddress.PlayerSlot(witness.playerId(), source.playerSlot())))
                    throw new IllegalArgumentException("player return lost its exact admitted binding");
                payload = FungiblePhysicalHandoff.returnObserved(current, binding, observed.returnedRemaining().orElseThrow(),
                        witness.accountId(), witness.containerId(), witness.authorityEpoch(), physical);
            } else payload = FungibleContainerPlayerEdit.classify(current, witness.accountId(), witness.containerId(),
                    witness.ownerId(), witness.authorityEpoch(), witness.playerId(), witness.interactionId(), physical,
                    PlayerStockClaimLoss.admissibleClaims(state, witness.accountId()));
            CommandResult result = FrontierV3CommandSubmission.submit(runtime, "depot-player-edit",
                    witness.interactionId().toString(), payload);
            if (result instanceof CommandResult.Accepted) {
                if (!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedContainerMutation(
                        runtime, witness.containerId(), physicalContainer)) return false;
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

    /** Only the clicked exact player slot supplies return authority; no inventory-wide stack matching. */
    private static java.util.Optional<FrontierV3DepotClickWitness.ReturnSource> playerReturnSource(ServerPlayer player,
            AbstractContainerMenu menu, int slot, ClickType kind, FrontierWorldState state) {
        var bindings = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.address() instanceof PhysicalStackAddress.PlayerSlot address
                        && address.playerId().equals(player.getUUID())).toList();
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
        if (kind == ClickType.PICKUP_ALL) return hasUnretirableClaim(account, state);
        if (slot < 0) return false;
        if (kind != ClickType.PICKUP && kind != ClickType.QUICK_MOVE && kind != ClickType.THROW) return false;
        var matching = bindings.stream().filter(binding -> binding.address() instanceof PhysicalStackAddress.ContainerSlot source
                && source.slot().slot() == slot).findFirst().orElse(null);
        if (matching == null) return false;
        if (!exceedsUnclaimedPortion(matching, kind, button, menu.getCarried().isEmpty())) return false;
        return hasUnretirableClaim(account, state);
    }

    private static boolean hasUnretirableClaim(CustodyAccount account, FrontierWorldState state) {
        return !PlayerStockClaimLoss.admissibleClaims(state, account.id()).containsAll(account.claimQuantities().keySet());
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

    static ContainerRecord ownedContainer(FrontierWorldState state, BlockPos position) {
        return state.inventory().surfaces().entrySet().stream()
                .filter(entry -> entry.getValue().fixed())
                .filter(entry -> entry.getValue().position().x() == position.getX()
                        && entry.getValue().position().y() == position.getY()
                        && entry.getValue().position().z() == position.getZ())
                .map(entry -> state.inventory().containers().get(entry.getKey()))
                .filter(value -> value != null && ReferenceContainerCustody.isReferenceContainer(state, value.id()))
                .findFirst().orElse(null);
    }

    private static boolean exactSlotsCurrent(FrontierWorldState state, SubjectId containerId,
                                             net.minecraft.world.Container chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ExactItemStack exact = state.inventory().itemAt(containerId, slot).orElse(null);
            if (exact != null && !FrontierV3ExactItemPresentation.exactMatch(chest.getItem(slot), exact)) return false;
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
