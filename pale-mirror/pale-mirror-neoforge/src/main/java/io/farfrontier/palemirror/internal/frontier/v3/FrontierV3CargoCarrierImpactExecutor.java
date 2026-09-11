package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemDestroyed;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalHandoff;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceHandoffObserved;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/** Reconciles one real external cargo-carrier aftermath from retained pre-impact evidence. */
final class FrontierV3CargoCarrierImpactExecutor {
    private static final int MAX_RECONCILIATIONS_PER_TICK = 16;

    private FrontierV3CargoCarrierImpactExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        tick(level, runtime, FrontierV3CargoCarrierImpactLedger.get(level), level.getGameTime());
    }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierV3CargoCarrierImpactLedger ledger) {
        tick(level, runtime, ledger, level.getGameTime());
    }

    /** Visible for a saved-data recovery proof: callers supply the post-impact inspection instant. */
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierV3CargoCarrierImpactLedger ledger, long inspectionGameTime) {
        for (int count = 0; count < MAX_RECONCILIATIONS_PER_TICK; count++) {
            var ready = ledger.nextReady(inspectionGameTime);
            if (ready.isEmpty()) return;
            if (ready.orElseThrow().fungible().isPresent()) reconcileFungible(level, runtime, ledger, ready.orElseThrow());
            else reconcileExact(level, runtime, ledger, ready.orElseThrow());
        }
    }

    private static void reconcileExact(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       FrontierV3CargoCarrierImpactLedger ledger, FrontierV3CargoCarrierImpactLedger.Ready ready) {
        BlockPos position = BlockPos.of(ready.position());
        if (!level.hasChunkAt(position)) return; // wait for ordinary loaded-world evidence; never ticket.
        FrontierWorldState state = runtime.decodedState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        InventoryCustody.WorldCarrier source = new InventoryCustody.WorldCarrier(ready.carrierId());
        ExactItemStack item = state.inventory().items().get(ready.itemId().orElseThrow());
        if (item == null || !item.custody().equals(source)) { ledger.resolve(ready); return; }

        Entity carrier = level.getEntity(ready.carrierId());
        if (carrier instanceof MinecartChest cart && !cart.isRemoved()) {
            if (!contains(cart, item, ready.carrierId())) throw new IllegalStateException("live cargo carrier lost an exact stack without a physical transfer receipt");
            ledger.resolve(ready); return;
        }
        if (carrier != null && !carrier.isRemoved()) throw new IllegalStateException("cargo carrier UUID was rebound to a foreign entity");

        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(16.0D), drop ->
                        FrontierV3CargoHandoffExecutor.exactMatch(drop.getItem(), item)
                                && FrontierV3CargoHandoffExecutor.worldCarrierId(drop.getItem()).filter(ready.carrierId()::equals).isPresent())
                .stream().sorted(Comparator.comparing(ItemEntity::getUUID)).toList();
        List<ServerPlayer> players = level.players().stream().filter(player -> player.distanceToSqr(position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D) <= 16.0D * 16.0D)
                .filter(player -> FrontierV3InventoryObservationExecutor.hasExactItem(player, item))
                .sorted(Comparator.comparing(ServerPlayer::getUUID)).toList();
        if (drops.size() + players.size() > 1) throw new IllegalStateException("external cargo impact has ambiguous exact physical custody");
        if (drops.size() == 1) {
            ItemEntity drop = drops.getFirst(); ItemStack stack = drop.getItem();
            FrontierV3CargoHandoffExecutor.bindWorldCarrier(stack, drop.getUUID()); drop.setItem(stack);
            FrontierV3CommandSubmission.submit(runtime, "cargo-impact-drop", item.id().value(),
                    new ExactItemCustodyChanged(item.id(), source, new InventoryCustody.WorldCarrier(drop.getUUID())));
            ledger.resolve(ready); return;
        }
        if (players.size() == 1) {
            FrontierV3CommandSubmission.submit(runtime, "cargo-impact-player", item.id().value(),
                    new ExactItemCustodyChanged(item.id(), source, new InventoryCustody.Player(players.getFirst().getUUID())));
            ledger.resolve(ready); return;
        }
        if (inNearbyHopper(level, position, item, ready.carrierId())) { ledger.resolve(ready); return; }
        FrontierV3CommandSubmission.submit(runtime, "cargo-impact-destroyed", item.id().value(),
                new ExactItemDestroyed(item.id(), source, "external-explosion"));
        ledger.resolve(ready);
    }

    /** Replays a retained HOT fungible cart aftermath only from the one captured account/binding fence. */
    private static void reconcileFungible(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                          FrontierV3CargoCarrierImpactLedger ledger, FrontierV3CargoCarrierImpactLedger.Ready ready) {
        BlockPos position = BlockPos.of(ready.position());
        if (!level.hasChunkAt(position)) return;
        FrontierV3CargoCarrierImpactLedger.Fungible retained = ready.fungible().orElseThrow();
        FrontierWorldState state = runtime.decodedState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CustodyAccount source = state.inventory().fungibleResources().accounts().get(retained.accountId());
        PhysicalStackBinding binding = state.inventory().fungibleResources().bindings().get(retained.bindingId());
        if (source == null || binding == null || !source.custody().equals(new ResourceCustody.WorldCarrier(ready.carrierId()))
                || !binding.accountId().equals(source.id()) || binding.authorityEpoch() != retained.authorityEpoch()
                || !binding.itemKind().equals(retained.itemKind()) || binding.quantity() != retained.quantity()) {
            ledger.resolve(ready); return;
        }
        Entity carrier = level.getEntity(ready.carrierId());
        if (carrier instanceof MinecartChest cart && !cart.isRemoved()) {
            if (!contains(cart, binding)) throw new IllegalStateException("live cargo carrier lost a fungible stack without a physical transfer receipt");
            ledger.resolve(ready); return;
        }
        if (carrier != null && !carrier.isRemoved()) throw new IllegalStateException("cargo carrier UUID was rebound to a foreign entity");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(16.0D),
                drop -> matches(drop.getItem(), binding)).stream().sorted(Comparator.comparing(ItemEntity::getUUID)).toList();
        if (drops.size() != 1) return; // no unambiguous physical destination: keep restart-safe evidence fenced
        ItemEntity drop = drops.getFirst();
        try {
            FungibleResourceHandoffObserved observed = FungiblePhysicalHandoff.departToNew(state.inventory().fungibleResources(), source.id(),
                    binding.authorityEpoch(), binding, 0, new io.farfrontier.palemirror.frontier.v3.api.SubjectId("custody:world-" + drop.getUUID()),
                    new ResourceCustody.WorldCarrier(drop.getUUID()), 1L, new PhysicalStackAddress.WorldEntity(drop.getUUID()));
            CommandResult result = FrontierV3CommandSubmission.submit(runtime, "fungible-cargo-impact-drop", source.id().value(), observed);
            if (result instanceof CommandResult.Accepted) ledger.resolve(ready);
        } catch (IllegalArgumentException ignored) {
            // A changed account, claims, or physical shape never authorizes an inferred loss.
        }
    }

    private static boolean contains(MinecartChest cart, ExactItemStack item, java.util.UUID carrierId) {
        for (int slot = 0; slot < cart.getContainerSize(); slot++) {
            if (FrontierV3CargoHandoffExecutor.exactMatch(cart.getItem(slot), item)
                    && FrontierV3CargoHandoffExecutor.worldCarrierId(cart.getItem(slot)).filter(carrierId::equals).isPresent()) return true;
        }
        return false;
    }

    private static boolean contains(MinecartChest cart, PhysicalStackBinding binding) {
        int found = 0;
        for (int slot = 0; slot < cart.getContainerSize(); slot++) {
            ItemStack stack = cart.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!matches(stack, binding) || ++found > 1) return false;
        }
        return found == 1;
    }

    private static boolean matches(ItemStack stack, PhysicalStackBinding binding) {
        return !stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(binding.itemKind())
                && stack.getCount() == binding.quantity();
    }

    /** A hopper may pull an exact drop before this server-thread observer runs; its old carrier ID remains valid provenance. */
    private static boolean inNearbyHopper(ServerLevel level, BlockPos center, ExactItemStack item, java.util.UUID carrierId) {
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            BlockPos position = center.offset(x, y, z);
            if (!level.hasChunkAt(position) || !(level.getBlockEntity(position) instanceof HopperBlockEntity hopper)) continue;
            for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
                ItemStack stack = hopper.getItem(slot);
                if (FrontierV3CargoHandoffExecutor.exactMatch(stack, item)
                        && FrontierV3CargoHandoffExecutor.worldCarrierId(stack).filter(carrierId::equals).isPresent()) return true;
            }
        }
        return false;
    }
}
