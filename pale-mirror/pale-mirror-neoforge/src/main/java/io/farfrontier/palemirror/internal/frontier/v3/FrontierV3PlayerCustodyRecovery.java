package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceHandoffObserved;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only reconciliation of retained player custody. Missing native evidence is
 * ambiguity, never permission to mint resources into the player's inventory. Legacy
 * markers remain diagnostic until their producers migrate to the common operation protocol. */
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
            Decision decision = decision(captured, FrontierV3PlayerResourceDiagnostic.actual(player, captured.slot()));
            pending.remove(captured.accountId());
            if (decision == Decision.LOCAL_AMBIGUITY) {
                PaleMirrorMod.LOGGER.warn("Frontier v3 retained player-custody local ambiguity account={} binding={}", captured.accountId(), captured.bindingId());
                return false;
            }
            if (decision == Decision.CONFLICT)
                PaleMirrorMod.LOGGER.warn("Frontier v3 retained changed player-custody slot account={} binding={}", captured.accountId(), captured.bindingId());
            return false;
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
        armDurablePlayerSave(player, observed.playerSaveFence(), observed.destinationAccount().id(), binding.id(), slot.slot());
    }

    private static void armDurablePlayerSave(ServerPlayer player, String token, SubjectId accountId, SubjectId bindingId, int slot) {
        CompoundTag fence = new CompoundTag();
        fence.putString("token", token); fence.putString("account", accountId.value());
        fence.putString("binding", bindingId.value()); fence.putInt("slot", slot);
        player.getPersistentData().put(SAVE_FENCE_KEY, fence);
    }

    /** Neither absence nor presence of an old marker authorizes a new physical effect. */
    static Decision decision(FrontierV3PlayerResourceDiagnostic.Expected expected, FrontierV3PlayerResourceDiagnostic.Actual actual) {
        if (expected.canonicalQuantity() != expected.bindingQuantity() || expected.bindingQuantity() < 1 || expected.playerSaveFence().isEmpty()) {
            return Decision.CONFLICT;
        }
        if (expected.itemKind().equals(actual.itemKind()) && expected.bindingQuantity() == actual.count()) return Decision.CURRENT;
        if (actual.count() == 0 && actual.itemKind().isEmpty()) {
            return Decision.LOCAL_AMBIGUITY;
        }
        return Decision.CONFLICT;
    }

    enum Decision { CURRENT, LOCAL_AMBIGUITY, CONFLICT }
}
