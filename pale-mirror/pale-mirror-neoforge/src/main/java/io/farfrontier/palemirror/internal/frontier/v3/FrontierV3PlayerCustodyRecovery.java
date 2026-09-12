package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * Reconstitutes the one fenced player slot that a durable HOT handoff had already
 * transferred to canonical custody when an abrupt process stop lost player NBT.
 *
 * <p>The pending set is captured once at runtime recovery.  Consequently a later
 * ordinary empty slot remains available to the normal player-return observer; it
 * cannot be mistaken for a crash repair.  A nonempty or structurally changed slot
 * is never overwritten.</p>
 */
final class FrontierV3PlayerCustodyRecovery {
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SubjectId, FrontierV3PlayerResourceDiagnostic.Expected>> PENDING = new IdentityHashMap<>();

    private FrontierV3PlayerCustodyRecovery() { }

    static void beginRecovery(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        Map<SubjectId, FrontierV3PlayerResourceDiagnostic.Expected> pending = new LinkedHashMap<>();
        state.inventory().fungibleResources().accounts().keySet().stream().sorted()
                .map(id -> FrontierV3PlayerResourceDiagnostic.expected(state, id.value()))
                .filter(java.util.Objects::nonNull).forEach(expected -> pending.put(expected.accountId(), expected));
        if (!pending.isEmpty()) PENDING.put(runtime, pending);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { PENDING.remove(runtime); }

    /** Performs at most one startup-fenced reconciliation per physical turn. */
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
            Decision decision = decision(captured, FrontierV3PlayerResourceDiagnostic.actual(player, captured.slot()));
            pending.remove(captured.accountId());
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

    /** Package-visible decision boundary: only an exact empty startup-fenced slot is reversible. */
    static Decision decision(FrontierV3PlayerResourceDiagnostic.Expected expected, FrontierV3PlayerResourceDiagnostic.Actual actual) {
        if (expected.canonicalQuantity() != expected.bindingQuantity() || expected.bindingQuantity() < 1) return Decision.CONFLICT;
        if (expected.itemKind().equals(actual.itemKind()) && expected.bindingQuantity() == actual.count()) return Decision.CURRENT;
        return actual.count() == 0 && actual.itemKind().isEmpty() ? Decision.MATERIALIZE : Decision.CONFLICT;
    }

    static ItemStack materialized(FrontierV3PlayerResourceDiagnostic.Expected expected) {
        if (expected.bindingQuantity() < 1 || expected.bindingQuantity() != expected.canonicalQuantity()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(expected.itemKind());
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || expected.bindingQuantity() > item.getDefaultMaxStackSize()) return ItemStack.EMPTY;
        return new ItemStack(item, expected.bindingQuantity());
    }

    enum Decision { CURRENT, MATERIALIZE, CONFLICT }
}
