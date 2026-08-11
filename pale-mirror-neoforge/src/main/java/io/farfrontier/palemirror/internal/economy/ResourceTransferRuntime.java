package io.farfrontier.palemirror.internal.economy;

import java.util.Comparator;
import java.util.UUID;

import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.ResourceAccount;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Restart-reconcilable item/canonical-stock bridge for PM-owned depots. */
public final class ResourceTransferRuntime {
    public static final String MAPPING_HASH = "iron-ingot-v1:sha256:6f86818b";
    public static final String TRANSFER_KEY = "pale_mirror_resource_transfer";
    private static final String DIRECTION_KEY = "pale_mirror_resource_direction";
    private static final int MAX_WITHDRAWAL = 16;

    private ResourceTransferRuntime() { }

    public static InteractionResult prepare(PaleMirrorSavedData data, DomainCommandProcessor commands,
                                            ServerPlayer player, net.minecraft.core.BlockPos position,
                                            StoryAudienceId audience) {
        SettlementDepotRecord depot = data.settlementDepots().values().stream()
                .filter(value -> value.state() == SettlementDepotState.ACTIVE
                        && value.dimensionId().equals(player.serverLevel().dimension().location().toString())
                        && value.interactionPosition().equals(position)).findFirst().orElse(null);
        if (depot == null) return InteractionResult.notHandled();
        if (data.resourceTransfers().hasActiveFor(player.getUUID())) return InteractionResult.failure("A resource transfer is already pending");
        var region = data.worldState().livingRegions().stream().filter(value -> value.communityId().equals(depot.communityId())).findFirst().orElse(null);
        if (region == null || region.primaryAudience() == null || !region.primaryAudience().equals(audience)) {
            return InteractionResult.failure("This depot belongs to another story audience");
        }
        ResourceAccount account = data.worldState().economy(depot.communityId()).orElseThrow().require(ResourceKind.IRON);
        ItemStack held = player.getMainHandItem();
        ResourceTransferDirection direction;
        ResourceTransferPurpose purpose = ResourceTransferPurpose.SETTLEMENT_STOCK;
        String intentId = "";
        int amount;
        if (held.is(Items.IRON_INGOT) && !isReserved(held)) {
            direction = ResourceTransferDirection.DEPOSIT;
            var project = data.worldState().developmentIntents().stream()
                    .filter(intent -> intent.communityId().equals(depot.communityId())
                            && intent.type() == io.farfrontier.palemirror.domain.DevelopmentIntentType.UPGRADE_STOREHOUSE
                            && intent.state() == io.farfrontier.palemirror.domain.DevelopmentIntentState.PLANNED
                            && intent.remainingAmount() > 0)
                    .filter(intent -> data.worldState().scenarios().stream().anyMatch(scenario ->
                            scenario.target().equals(depot.communityId())
                                    && scenario.archetype() == io.farfrontier.palemirror.domain.ScenarioArchetype.DEVELOPMENT_OPPORTUNITY
                                    && scenario.status() == io.farfrontier.palemirror.domain.ScenarioStatus.RESPOND))
                    .findFirst().orElse(null);
            if (project != null) {
                purpose = ResourceTransferPurpose.DEVELOPMENT_PROJECT;
                intentId = project.id();
                amount = Math.min(held.getCount(), project.remainingAmount());
            } else {
                amount = Math.min(held.getCount(), account.capacity() - account.stock());
                if (amount <= 0) return InteractionResult.failure("Iron reserve is already full");
            }
        } else if (held.isEmpty() && player.isShiftKeyDown()) {
            direction = ResourceTransferDirection.WITHDRAWAL;
            int emergencyReserve = account.effectiveConsumption() * 2;
            amount = Math.min(MAX_WITHDRAWAL, account.stock() - emergencyReserve);
            if (amount <= 0) return InteractionResult.failure("Withdrawal would consume the emergency reserve");
            boolean quotaUsed = data.resourceTransfers().transfers().stream().anyMatch(value ->
                    value.direction() == ResourceTransferDirection.WITHDRAWAL && value.playerId().equals(player.getUUID())
                            && value.createdStep() == data.worldState().simulationStep() && value.state() != ResourceTransferState.CANCELLED);
            if (quotaUsed) return InteractionResult.failure("Withdrawal quota is already used for this simulation step");
        } else return InteractionResult.failure("Use iron ingots to deposit, or sneak with an empty hand to withdraw");
        String id = "pm:resource:" + UUID.randomUUID();
        data.resourceTransfers().add(new ResourceTransfer(id, direction, player.getUUID(), depot.communityId(),
                depot.siteId(), ResourceKind.IRON, amount, player.getInventory().selected, MAPPING_HASH,
                data.worldState().simulationStep(), purpose, intentId, ResourceTransferState.PREPARED, ""));
        return InteractionResult.success("Prepared " + (purpose == ResourceTransferPurpose.DEVELOPMENT_PROJECT
                ? "storehouse contribution" : direction.name().toLowerCase()) + " of " + amount + " IRON");
    }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        boolean changed = false;
        for (ResourceTransfer transfer : data.resourceTransfers().transfers().stream()
                .filter(value -> !value.state().terminal()).sorted(Comparator.comparing(ResourceTransfer::id)).toList()) {
            ServerPlayer player = server.getPlayerList().getPlayer(transfer.playerId());
            if (player == null) continue;
            if (!MAPPING_HASH.equals(transfer.mappingHash())) {
                transfer.block("Pinned resource mapping is unavailable");
                changed = true;
                continue;
            }
            try {
                changed |= transfer.direction() == ResourceTransferDirection.DEPOSIT
                        ? advanceDeposit(data, commands, player, transfer)
                        : advanceWithdrawal(data, commands, player, transfer);
            } catch (RuntimeException failure) {
                transfer.block(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
                changed = true;
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) changed |= reconcileOrphans(data, player);
        if (data.resourceTransfers().compact(data.worldState().simulationStep())) changed = true;
        return changed;
    }

    private static boolean advanceDeposit(PaleMirrorSavedData data, DomainCommandProcessor commands,
                                          ServerPlayer player, ResourceTransfer transfer) {
        ItemStack stack = player.getInventory().getItem(transfer.inventorySlot());
        if (transfer.state() == ResourceTransferState.PREPARED) {
            if (!stack.is(Items.IRON_INGOT) || stack.getCount() < transfer.amount() || isReserved(stack)) {
                transfer.cancel("The prepared iron stack is no longer available");
                return true;
            }
            mark(stack, transfer);
            transfer.physicalReserved();
            return true;
        }
        if (transfer.state() == ResourceTransferState.PHYSICAL_RESERVED) {
            if (!matches(stack, transfer) || stack.getCount() < transfer.amount()) {
                transfer.block("Reserved deposit stack is missing or changed");
                return true;
            }
            if (transfer.purpose() == ResourceTransferPurpose.DEVELOPMENT_PROJECT) {
                commands.execute(data.worldState(), new DomainCommand.ContributeDevelopmentIntent(
                        transfer.developmentIntentId(), transfer.amount(), transfer.id()));
            } else {
                commands.execute(data.worldState(), new DomainCommand.DepositResource(transfer.communityId(),
                        transfer.resource(), transfer.amount(), transfer.id()));
            }
            transfer.domainApplied();
            return true;
        }
        if (transfer.state() == ResourceTransferState.DOMAIN_APPLIED) {
            if (!matches(stack, transfer) || stack.getCount() < transfer.amount()) {
                transfer.block("Applied deposit cannot consume its reserved stack");
                return true;
            }
            stack.shrink(transfer.amount());
            clearMarker(stack);
            transfer.complete();
            player.containerMenu.broadcastChanges();
            return true;
        }
        return false;
    }

    private static boolean advanceWithdrawal(PaleMirrorSavedData data, DomainCommandProcessor commands,
                                             ServerPlayer player, ResourceTransfer transfer) {
        if (transfer.state() == ResourceTransferState.PREPARED) {
            ResourceAccount account = data.worldState().economy(transfer.communityId()).orElseThrow().require(transfer.resource());
            int reserve = account.effectiveConsumption() * 2;
            if (commands.execute(data.worldState(), new DomainCommand.WithdrawResource(transfer.communityId(),
                    transfer.resource(), transfer.amount(), reserve, transfer.id())).isEmpty()) {
                transfer.cancel("Canonical emergency reserve no longer permits this withdrawal");
            } else transfer.domainApplied();
            return true;
        }
        if (transfer.state() == ResourceTransferState.DOMAIN_APPLIED) {
            int emptySlot = firstEmptySlot(player);
            if (emptySlot < 0) return false;
            ItemStack output = new ItemStack(Items.IRON_INGOT, transfer.amount());
            mark(output, transfer);
            player.getInventory().setItem(emptySlot, output);
            transfer.physicalReserved();
            player.containerMenu.broadcastChanges();
            return true;
        }
        if (transfer.state() == ResourceTransferState.PHYSICAL_RESERVED) {
            boolean found = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (matches(stack, transfer)) { clearMarker(stack); found = true; }
            }
            if (!found) {
                transfer.block("Reserved withdrawal output is missing");
            } else transfer.complete();
            player.containerMenu.broadcastChanges();
            return true;
        }
        return false;
    }

    private static boolean reconcileOrphans(PaleMirrorSavedData data, ServerPlayer player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            String id = transferId(stack);
            if (id.isBlank()) continue;
            ResourceTransfer transfer = data.resourceTransfers().find(id).orElse(null);
            if (transfer == null) {
                String direction = direction(stack);
                if (ResourceTransferDirection.WITHDRAWAL.name().equals(direction)) stack.setCount(0);
                else clearMarker(stack);
                changed = true;
            } else if (transfer.state().terminal()) {
                clearMarker(stack);
                changed = true;
            }
        }
        return changed;
    }

    public static boolean isReserved(ItemStack stack) { return !transferId(stack).isBlank(); }
    private static boolean matches(ItemStack stack, ResourceTransfer transfer) { return transfer.id().equals(transferId(stack)); }
    private static void mark(ItemStack stack, ResourceTransfer transfer) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TRANSFER_KEY, transfer.id());
            tag.putString(DIRECTION_KEY, transfer.direction().name());
        });
    }
    private static void clearMarker(ItemStack stack) {
        if (stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(TRANSFER_KEY);
            tag.remove(DIRECTION_KEY);
        });
    }
    private static String transferId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(TRANSFER_KEY);
    }
    private static String direction(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(DIRECTION_KEY);
    }

    private static int firstEmptySlot(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    public record InteractionResult(boolean handled, boolean success, String message) {
        static InteractionResult notHandled() { return new InteractionResult(false, false, ""); }
        static InteractionResult success(String message) { return new InteractionResult(true, true, message); }
        static InteractionResult failure(String message) { return new InteractionResult(true, false, message); }
    }
}
