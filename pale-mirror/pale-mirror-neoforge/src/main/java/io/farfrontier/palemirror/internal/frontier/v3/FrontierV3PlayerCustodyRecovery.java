package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceHandoffObserved;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reconstitutes one player slot only when the exact durable canonical-first handoff has no
 * durable post-handoff player-save witness. The token is recorded in canonical HOT custody and
 * immediately armed in the live player's persistent NBT after the accepted observation; an
 * ordinary later player save carries that witness even if the player then consumes, moves or
 * drops the stack. Such divergence is local ambiguity, never authority to mint a replacement.
 */
final class FrontierV3PlayerCustodyRecovery {
    private static final String SAVE_FENCE_KEY = "pale_mirror.frontier_v3.player_custody_save_fence";
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SubjectId, FrontierV3PlayerResourceDiagnostic.Expected>> PENDING = new IdentityHashMap<>();

    private FrontierV3PlayerCustodyRecovery() { }

    static void beginRecovery(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        Map<SubjectId, FrontierV3PlayerResourceDiagnostic.Expected> pending = new LinkedHashMap<>();
        state.inventory().fungibleResources().accounts().keySet().stream().sorted()
                .map(id -> FrontierV3PlayerResourceDiagnostic.expected(state, id.value()))
                .filter(java.util.Objects::nonNull).filter(expected -> !expected.playerSaveFence().isEmpty())
                .forEach(expected -> pending.put(expected.accountId(), expected));
        if (!pending.isEmpty()) PENDING.put(runtime, pending);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { PENDING.remove(runtime); }

    /** Performs at most one exact startup-fenced reconciliation per physical turn. */
    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        Map<SubjectId, FrontierV3PlayerResourceDiagnostic.Expected> pending = PENDING.get(runtime);
        if (pending == null || pending.isEmpty()) return false;
        for (FrontierV3PlayerResourceDiagnostic.Expected captured : pending.values().stream()
                .sorted(Comparator.comparing(FrontierV3PlayerResourceDiagnostic.Expected::accountId)).toList()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(captured.playerId());
            if (player == null) continue;
            FrontierV3PlayerResourceDiagnostic.Expected current = FrontierV3PlayerResourceDiagnostic.expected(state, captured.accountId().value());
            if (!captured.equals(current)) {
                pending.remove(captured.accountId());
                PaleMirrorMod.LOGGER.warn("Frontier v3 fenced player-custody recovery ignored stale account={}", captured.accountId());
                return false;
            }
            Decision decision = decision(captured, FrontierV3PlayerResourceDiagnostic.actual(player, captured.slot()), hasDurablePlayerSave(player, captured));
            pending.remove(captured.accountId());
            if (decision == Decision.LOCAL_AMBIGUITY) {
                PaleMirrorMod.LOGGER.warn("Frontier v3 retained player-custody local ambiguity account={} binding={}", captured.accountId(), captured.bindingId());
                return false;
            }
            if (decision != Decision.MATERIALIZE) return false;
            ItemStack stack = materialized(captured);
            if (stack.isEmpty()) {
                PaleMirrorMod.LOGGER.error("Frontier v3 fenced player-custody recovery rejected invalid binding={}", captured.bindingId());
                return false;
            }
            player.getInventory().setItem(captured.slot(), stack);
            player.containerMenu.broadcastChanges();
            PaleMirrorMod.LOGGER.info("Frontier v3 restored fenced player custody account={} player={} slot={} kind={} count={}",
                    captured.accountId(), captured.playerId(), captured.slot(), captured.itemKind(), captured.bindingQuantity());
            return true;
        }
        return false;
    }

    /** Arms the exact token that Vanilla will persist with the next real player data save. */
    static void armDurablePlayerSave(ServerPlayer player, FungibleResourceHandoffObserved observed) {
        if (!(observed.destinationAccount().custody() instanceof ResourceCustody.Player custody)
                || !custody.playerId().equals(player.getUUID()) || observed.playerSaveFence().isEmpty()
                || observed.destinationBindings().size() != 1) return;
        PhysicalStackBinding binding = observed.destinationBindings().getFirst();
        if (!(binding.address() instanceof PhysicalStackAddress.PlayerSlot slot)
                || slot.slot() < 0 || slot.slot() >= player.getInventory().getContainerSize()) return;
        CompoundTag fence = new CompoundTag();
        fence.putString("token", observed.playerSaveFence()); fence.putString("account", observed.destinationAccount().id().value());
        fence.putString("binding", binding.id().value()); fence.putInt("slot", slot.slot());
        player.getPersistentData().put(SAVE_FENCE_KEY, fence);
    }

    /** Package-visible decision boundary: only a missing exact post-handoff save is reversible. */
    static Decision decision(FrontierV3PlayerResourceDiagnostic.Expected expected, FrontierV3PlayerResourceDiagnostic.Actual actual,
                             boolean durablePlayerSaveSeen) {
        if (expected.canonicalQuantity() != expected.bindingQuantity() || expected.bindingQuantity() < 1 || expected.playerSaveFence().isEmpty()) {
            return Decision.CONFLICT;
        }
        if (expected.itemKind().equals(actual.itemKind()) && expected.bindingQuantity() == actual.count()) return Decision.CURRENT;
        if (actual.count() == 0 && actual.itemKind().isEmpty()) {
            return durablePlayerSaveSeen ? Decision.LOCAL_AMBIGUITY : Decision.MATERIALIZE;
        }
        return Decision.CONFLICT;
    }

    private static boolean hasDurablePlayerSave(ServerPlayer player, FrontierV3PlayerResourceDiagnostic.Expected expected) {
        CompoundTag fence = player.getPersistentData().getCompound(SAVE_FENCE_KEY);
        return expected.playerSaveFence().equals(fence.getString("token"))
                && expected.accountId().value().equals(fence.getString("account"))
                && expected.bindingId().value().equals(fence.getString("binding")) && expected.slot() == fence.getInt("slot");
    }

    static ItemStack materialized(FrontierV3PlayerResourceDiagnostic.Expected expected) {
        if (expected.bindingQuantity() < 1 || expected.bindingQuantity() != expected.canonicalQuantity()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(expected.itemKind());
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || expected.bindingQuantity() > item.getDefaultMaxStackSize()) return ItemStack.EMPTY;
        return new ItemStack(item, expected.bindingQuantity());
    }

    enum Decision { CURRENT, MATERIALIZE, LOCAL_AMBIGUITY, CONFLICT }
}
