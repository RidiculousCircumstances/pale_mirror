package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalHandoff;
import io.farfrontier.palemirror.frontier.v3.model.FungibleClaimForfeitureStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceHandoffObserved;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStackBindingsReleased;
import io.farfrontier.palemirror.frontier.v3.model.FungibleStackLayoutObserved;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Converts a naturally ticking owned chest layout into the one temporary fungible binding
 * epoch. It never writes stacks, adopts foreign stock, or creates a fallback COLD balance.
 */
final class FrontierV3FungibleResourceObservationExecutor {
    private FrontierV3FungibleResourceObservationExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        if (FrontierV3ActorDeathResourceComposition.reconcileOne(level, runtime, state)) return;
        if (FrontierV3UnitInventoryPhysicalCustody.bindOne(level, runtime, state)) return;
        if (FrontierV3ContainerClickExecutor.reconcileOne(level, runtime, state)) return;
        // This is deliberately before ordinary return/departure observation: only an exact
        // canonical-first fence without its later persisted player witness is reversible.
        if (FrontierV3PlayerCustodyRecovery.reconcileOne(level, runtime, state)) return;
        if (observeOneWorldContainerDeparture(level, runtime, state)) return;
        if (observeOneWorldPickup(level, runtime, state)) return;
        for (CustodyAccount account : state.inventory().fungibleResources().accounts().values().stream()
                .filter(value -> value.custody() instanceof ResourceCustody.Container)
                .sorted(Comparator.comparing(CustodyAccount::id)).toList()) {
            ResourceCustody.Container custody = (ResourceCustody.Container) account.custody();
            if (FrontierV3DepotClickLedger.get(level).pending(custody.containerId()) != null) continue;
            if (FrontierV3ContainerEffectFence.pending(level, state, custody.containerId())) continue;
            ContainerSurface surface = state.inventory().surfaces().get(custody.containerId());
            if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) continue;
            var physical = FrontierV3PhysicalContainer.loaded(level, state, custody.containerId()).orElse(null);
            if (physical == null || !level.shouldTickBlocksAt(physical.position())) continue;
            PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(custody.containerId()));
            if (lease != null && lease.status() == PhysicalCustodyLeaseStatus.CHECKPOINTED) {
                release(runtime, account, lease);
                return;
            }
            if (ReferenceContainerCustody.isReferenceContainer(state, custody.containerId())
                    && (lease == null || !lease.live() || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED)) continue;
            long epoch = lease == null ? nextEpoch(state, custody.containerId()) : lease.authorityEpoch();
            if (!observe(level, runtime, state, account, physical.inventory(), physical.position(), epoch)) return;
        }
    }

    static boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                           CustodyAccount account, ChestBlockEntity chest, long epoch) {
        return observe(level, runtime, state, account, chest, chest.getBlockPos(), epoch);
    }

    private static boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                   FrontierWorldState state, CustodyAccount account, Container chest,
                                   BlockPos sourcePosition, long epoch) {
        SubjectId containerId = ((ResourceCustody.Container) account.custody()).containerId();
        if (FrontierV3ContainerEffectFence.pending(level, state, containerId)) return false;
        if (FrontierV3ContainerSurfaceExecutor.hasForeignFungibleComponents(chest, state, containerId)) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, containerId);
            return false;
        }
        List<FungiblePhysicalObservation.Stack> stacks = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state,
                containerId);
        if (FrontierV3ContainerSurfaceExecutor.overlapsHarvestOutputReservation(state, stacks)) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, ((ResourceCustody.Container) account.custody()).containerId());
            return false;
        }
        List<PhysicalStackBinding> expected;
        try {
            expected = FungiblePhysicalObservation.bind(state.inventory().fungibleResources(), account.id(), epoch, stacks);
        } catch (IllegalArgumentException invalid) {
            // Player menu edits are accounted only by their durable causal witness.
            // Matching another player's same-kind stack is not evidence of a container click.
            if (observeOneExternalDeparture(level, runtime, state, account, chest, sourcePosition, epoch)) return false;
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, ((ResourceCustody.Container) account.custody()).containerId());
            return false;
        }
        List<PhysicalStackBinding> current = state.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(account.id())).sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
        if (current.equals(expected)) return true;
        if (!current.isEmpty() && current.stream().anyMatch(binding -> binding.authorityEpoch() != epoch)) {
            FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, ((ResourceCustody.Container) account.custody()).containerId());
            return false;
        }
        CommandResult result = FrontierV3CommandSubmission.submit(runtime, "fungible-layout", account.id().value(),
                new FungibleStackLayoutObserved(account.id(), epoch, stacks));
        return result instanceof CommandResult.Accepted;
    }


    /**
     * One ordinary player withdrawal is a typed partial handoff, not a generic chest conflict.
     * Ambiguous stacks, existing player custody and any non-current source layout still fail
     * closed; this adapter never writes either inventory.
     */

    /** One source stack can leave to exactly one hopper slot or world entity, never inferred COLD stock. */
    private static boolean observeOneExternalDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                       FrontierWorldState state, CustodyAccount account, Container chest,
                                                       BlockPos sourcePosition, long epoch) {
        List<PhysicalStackBinding> current = current(state.inventory().fungibleResources(), account.id());
        if (current.isEmpty() || current.stream().anyMatch(binding -> binding.authorityEpoch() != epoch)) return false;
        List<SourceDeparture> departures = current.stream().map(binding -> departure(chest, binding)).filter(java.util.Objects::nonNull).toList();
        if (departures.size() != 1 || current.stream().filter(binding -> !binding.id().equals(departures.getFirst().binding().id()))
                .anyMatch(binding -> !matches(chest, binding))) return false;
        SourceDeparture source = departures.getFirst(); List<ExternalTarget> targets = externalTargets(level, sourcePosition, source);
        if (targets.size() != 1) return false;
        ExternalTarget target = targets.getFirst();
        try {
            FungibleResourceHandoffObserved observed = committedDeparture(state, FungiblePhysicalHandoff.departToNew(state.inventory().fungibleResources(), account.id(), epoch,
                    source.binding(), source.remainingQuantity(), target.accountId(), target.custody(), 1L, target.address()));
            if (observed == null) return false;
            CommandResult result = FrontierV3CommandSubmission.submit(runtime, "fungible-external-departure", account.id().value(), observed);
            return result instanceof CommandResult.Accepted;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** A dropped HOT stack remains its one world-carrier account until one real player pickup is observed. */
    private static boolean observeOneWorldPickup(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        for (CustodyAccount source : state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.WorldCarrier).sorted(Comparator.comparing(CustodyAccount::id)).toList()) {
            List<PhysicalStackBinding> current = current(state.inventory().fungibleResources(), source.id());
            if (current.size() != 1 || !(current.getFirst().address() instanceof PhysicalStackAddress.WorldEntity address)) continue;
            if (level.getEntity(address.entityId()) instanceof ItemEntity entity && matches(entity.getItem(), current.getFirst())) continue;
            List<PlayerStack> targets = level.players().stream().flatMap(player -> java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
                    .mapToObj(slot -> new PlayerStack(player, slot, player.getInventory().getItem(slot))))
                    .filter(target -> matches(target.stack(), current.getFirst())).sorted(Comparator.comparing((PlayerStack value) -> value.player().getUUID())
                            .thenComparingInt(PlayerStack::slot)).toList();
            if (targets.size() != 1) continue;
            PlayerStack target = targets.getFirst(); UUID playerId = target.player().getUUID();
            if (state.inventory().fungibleResources().accounts().values().stream().anyMatch(account -> account.custody().equals(new ResourceCustody.Player(playerId)))) continue;
            try {
                FungibleResourceHandoffObserved observed = committedDeparture(state, FungiblePhysicalHandoff.departToNew(state.inventory().fungibleResources(), source.id(),
                        current.getFirst().authorityEpoch(), current.getFirst(), 0, new SubjectId("custody:player-" + playerId),
                        new ResourceCustody.Player(playerId), 1L, new PhysicalStackAddress.PlayerSlot(playerId, target.slot())));
                if (observed == null) continue;
                observed = observed.withPlayerSaveFence(UUID.randomUUID());
                CommandResult result = FrontierV3CommandSubmission.submit(runtime, "fungible-world-pickup", source.id().value(), observed);
                if (result instanceof CommandResult.Accepted) {
                    FrontierV3PlayerCustodyRecovery.armDurablePlayerSave(target.player(), observed);
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // This is unverifiable physical drift; retaining the current HOT account is fail closed.
            }
        }
        return false;
    }

    /**
     * A declared world container is the physical carrier for the same account, not a second
     * inventory.  Its only admitted ordinary departure is one unambiguous player stack whose
     * quantity exactly explains the cart slot's observed reduction.
     */
    private static boolean observeOneWorldContainerDeparture(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                           FrontierWorldState state) {
        for (CustodyAccount source : state.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody() instanceof ResourceCustody.WorldCarrier)
                .sorted(Comparator.comparing(CustodyAccount::id)).toList()) {
            List<PhysicalStackBinding> current = current(state.inventory().fungibleResources(), source.id());
            if (current.size() != 1 || !(current.getFirst().address() instanceof PhysicalStackAddress.WorldEntity address)) continue;
            if (!(level.getEntity(address.entityId()) instanceof MinecartChest cart) || cart.isRemoved()) continue;
            PhysicalStackBinding binding = current.getFirst(); ItemStack remaining = cargoStack(cart, binding);
            if (remaining == null || remaining.getCount() == binding.quantity()) continue;
            int moved = binding.quantity() - remaining.getCount();
            List<PlayerStack> targets = level.players().stream().flatMap(player -> java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
                    .mapToObj(slot -> new PlayerStack(player, slot, player.getInventory().getItem(slot))))
                    .filter(target -> sameKindAndQuantity(target.stack(), binding, moved)).sorted(Comparator.comparing((PlayerStack value) -> value.player().getUUID())
                            .thenComparingInt(PlayerStack::slot)).toList();
            if (targets.size() != 1) continue;
            PlayerStack target = targets.getFirst(); UUID playerId = target.player().getUUID();
            if (state.inventory().fungibleResources().accounts().values().stream()
                    .anyMatch(account -> account.custody().equals(new ResourceCustody.Player(playerId)))) continue;
            try {
                FungibleResourceHandoffObserved observed = committedDeparture(state, FungiblePhysicalHandoff.departToNew(
                        state.inventory().fungibleResources(), source.id(), binding.authorityEpoch(), binding, remaining.getCount(),
                        new SubjectId("custody:player-" + playerId), new ResourceCustody.Player(playerId), 1L,
                        new PhysicalStackAddress.PlayerSlot(playerId, target.slot())));
                if (observed == null) continue;
                observed = observed.withPlayerSaveFence(UUID.randomUUID());
                CommandResult result = FrontierV3CommandSubmission.submit(runtime, "fungible-cargo-carrier-withdrawal", source.id().value(), observed);
                if (result instanceof CommandResult.Accepted) {
                    FrontierV3PlayerCustodyRecovery.armDurablePlayerSave(target.player(), observed);
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Physical stacks that cannot prove one complete custody transaction remain fenced.
            }
        }
        return false;
    }

    private static ItemStack cargoStack(MinecartChest cart, PhysicalStackBinding binding) {
        ItemStack observed = ItemStack.EMPTY;
        for (int slot = 0; slot < cart.getContainerSize(); slot++) {
            ItemStack stack = cart.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!observed.isEmpty() || !kind(stack).equals(binding.itemKind()) || stack.getCount() > binding.quantity()) return null;
            observed = stack;
        }
        return observed;
    }

    private static SourceDeparture departure(Container chest, PhysicalStackBinding binding) {
        if (!(binding.address() instanceof PhysicalStackAddress.ContainerSlot address)) return null;
        ItemStack actual = chest.getItem(address.slot().slot());
        if (!actual.isEmpty() && !kind(actual).equals(binding.itemKind())) return null;
        int remaining = actual.isEmpty() ? 0 : actual.getCount();
        return remaining < binding.quantity() ? new SourceDeparture(binding, remaining, binding.quantity() - remaining) : null;
    }

    private static boolean matches(Container chest, PhysicalStackBinding binding) {
        if (!(binding.address() instanceof PhysicalStackAddress.ContainerSlot address)) return false;
        ItemStack actual = chest.getItem(address.slot().slot());
        return !actual.isEmpty() && kind(actual).equals(binding.itemKind()) && actual.getCount() == binding.quantity();
    }

    private static boolean matches(ItemStack stack, PhysicalStackBinding binding) {
        return !stack.isEmpty() && kind(stack).equals(binding.itemKind()) && stack.getCount() == binding.quantity();
    }

    private static List<PhysicalStackBinding> current(FungibleResourceLedger resources, SubjectId accountId) {
        return resources.bindings().values().stream().filter(binding -> binding.accountId().equals(accountId))
                .sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
    }

    /** A physical departure may take live input only through its owner's atomic forfeiture path. */
    private static FungibleResourceHandoffObserved committedDeparture(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        FungibleResourceHandoffObserved committed = observed.forfeitAffectedClaims(state.inventory().fungibleResources());
        if (committed.forfeitedClaimIds().isEmpty()) return committed;
        committed = FungibleClaimForfeitureStateSupport.stampOwnerDiagnostic(state, committed);
        return FungibleClaimForfeitureStateSupport.supports(state, committed) ? committed : null;
    }

    private static List<ExternalTarget> externalTargets(ServerLevel level, BlockPos source, SourceDeparture departure) {
        List<HopperCandidate> hoppers = java.util.stream.Stream.of(source.above(), source.below(), source.north(), source.south(), source.east(), source.west())
                .filter(level::hasChunkAt).map(level::getBlockEntity).filter(HopperBlockEntity.class::isInstance).map(HopperBlockEntity.class::cast)
                .flatMap(hopper -> java.util.stream.IntStream.range(0, hopper.getContainerSize()).mapToObj(slot -> new HopperCandidate(hopper, slot, hopper.getItem(slot))))
                .filter(candidate -> sameKindAndQuantity(candidate.stack(), departure.binding(), departure.movedQuantity()))
                .sorted(Comparator.comparingLong(candidate -> candidate.hopper().getBlockPos().asLong())).toList();
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(source).inflate(4.0D), entity ->
                sameKindAndQuantity(entity.getItem(), departure.binding(), departure.movedQuantity())).stream()
                .sorted(Comparator.comparing(ItemEntity::getUUID)).toList();
        if (hoppers.size() + drops.size() != 1) return List.of();
        if (!drops.isEmpty()) {
            ItemEntity drop = drops.getFirst();
            return List.of(new ExternalTarget(new SubjectId("custody:world-" + drop.getUUID()), new ResourceCustody.WorldCarrier(drop.getUUID()),
                    new PhysicalStackAddress.WorldEntity(drop.getUUID())));
        }
        HopperCandidate hopper = hoppers.getFirst();
        FrontierV3InventoryObservationExecutor.HopperCarrierBinding claim = FrontierV3InventoryObservationExecutor.bindHopperCarrier(
                FrontierV3HopperCarrierLedger.get(level), hopper.hopper(), hopper.slot());
        if (claim.status() == FrontierV3InventoryObservationExecutor.HopperCarrierStatus.CONFLICT) return List.of();
        BlockPos position = hopper.hopper().getBlockPos(); UUID carrier = claim.carrierId();
        return List.of(new ExternalTarget(new SubjectId("custody:hopper-" + carrier), new ResourceCustody.WorldCarrier(carrier),
                new PhysicalStackAddress.HopperSlot(new BlockPosition(position.getX(), position.getY(), position.getZ()), hopper.slot())));
    }

    private static boolean sameKindAndQuantity(ItemStack stack, PhysicalStackBinding binding, int quantity) {
        return !stack.isEmpty() && kind(stack).equals(binding.itemKind()) && stack.getCount() == quantity;
    }

    private static String kind(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }

    private record SourceDeparture(PhysicalStackBinding binding, int remainingQuantity, int movedQuantity) { }
    private record PlayerStack(ServerPlayer player, int slot, ItemStack stack) { }
    private record HopperCandidate(HopperBlockEntity hopper, int slot, ItemStack stack) { }
    private record ExternalTarget(SubjectId accountId, ResourceCustody custody, PhysicalStackAddress address) { }

    private static void release(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, CustodyAccount account, PhysicalCustodyLease lease) {
        FungibleResourceLedger resources = runtime.decodedState().orElseThrow().inventory().fungibleResources();
        List<PhysicalStackBinding> current = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
        if (current.isEmpty()) return;
        if (current.stream().anyMatch(binding -> binding.authorityEpoch() != lease.authorityEpoch())) {
            throw new IllegalStateException("fungible resource binding has a stale reference custody epoch");
        }
        FrontierV3CommandSubmission.submit(runtime, "fungible-release", account.id().value(),
                new FungibleStackBindingsReleased(account.id(), lease.authorityEpoch()));
    }

    private static long nextEpoch(FrontierWorldState state, SubjectId containerId) {
        return state.replicaCustody().custodyByScope().values().stream().filter(lease -> lease.objectId().equals(containerId))
                .mapToLong(PhysicalCustodyLease::authorityEpoch).max().orElse(0L) + 1L;
    }
}
