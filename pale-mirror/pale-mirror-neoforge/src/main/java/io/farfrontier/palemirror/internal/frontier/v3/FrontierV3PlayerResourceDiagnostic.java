package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Read-only comparison of one canonical player-custody account with its authenticated live
 * Minecraft slot.  It is intentionally a diagnostic, not a recovery adapter: it never writes an
 * inventory stack, adopts a foreign stack, advances the runtime, or changes a WAL/snapshot.
 */
final class FrontierV3PlayerResourceDiagnostic {
    private FrontierV3PlayerResourceDiagnostic() { }

    static String render(FrontierScheduleView checkpoint, FrontierWorldState state, MinecraftServer server, String id) {
        Expected expected = expected(state, id);
        if (expected == null) return FrontierV3DiagnosticJson.bounded("player_resource", id, checkpoint,
                FrontierV3DiagnosticJson.unavailable("player_resource", id, checkpoint, "not_found"));
        ServerPlayer player = server.getPlayerList().getPlayer(expected.playerId());
        if (player == null) return FrontierV3DiagnosticJson.bounded("player_resource", id, checkpoint,
                FrontierV3DiagnosticJson.unavailable("player_resource", id, checkpoint, "player_offline"));
        return render(checkpoint, id, expected, actual(player, expected.slot()));
    }

    /** Package-visible pure selection keeps missing, split, and incompatible custody fail-closed. */
    static Expected expected(FrontierWorldState state, String id) {
        SubjectId accountId;
        try {
            accountId = new SubjectId(id);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        var resources = state.inventory().fungibleResources();
        CustodyAccount account = resources.accounts().get(accountId);
        if (account == null || !(account.custody() instanceof ResourceCustody.Player custody)) return null;
        List<PhysicalStackBinding> bindings = resources.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(accountId)).sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
        if (bindings.size() != 1) return null;
        PhysicalStackBinding binding = bindings.getFirst();
        if (!(binding.address() instanceof PhysicalStackAddress.PlayerSlot)
                || !((PhysicalStackAddress.PlayerSlot) binding.address()).playerId().equals(custody.playerId())) return null;
        PhysicalStackAddress.PlayerSlot address = (PhysicalStackAddress.PlayerSlot) binding.address();
        int canonicalQuantity = account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
        if (canonicalQuantity != binding.quantity()) return null;
        return new Expected(account.id(), custody.playerId(), address.slot(), canonicalQuantity, binding.id(), binding.authorityEpoch(),
                binding.itemKind(), binding.quantity(), binding.playerSaveFence());
    }

    /** Package-visible immutable physical read for focused tests; production supplies a live ServerPlayer only. */
    static Actual actual(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) return new Actual("", 0);
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) return new Actual("", 0);
        return new Actual(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
    }

    static String render(FrontierScheduleView checkpoint, String id, Expected expected, Actual actual) {
        boolean matches = expected.canonicalQuantity() == expected.bindingQuantity()
                && expected.itemKind().equals(actual.itemKind()) && expected.bindingQuantity() == actual.count();
        String value = FrontierV3DiagnosticJson.base("player_resource", id, checkpoint)
                + ",\"status\":\"" + (matches ? "ok" : "mismatch") + "\",\"account\":\""
                + FrontierV3DiagnosticJson.quote(expected.accountId().value()) + "\",\"player\":\"" + expected.playerId()
                + "\",\"slot\":" + expected.slot() + ",\"canonicalQuantity\":" + expected.canonicalQuantity()
                + ",\"binding\":{\"id\":\"" + FrontierV3DiagnosticJson.quote(expected.bindingId().value())
                + "\",\"epoch\":" + expected.authorityEpoch() + ",\"itemKind\":\""
                + FrontierV3DiagnosticJson.quote(expected.itemKind()) + "\",\"quantity\":" + expected.bindingQuantity()
                + "},\"actual\":{\"itemKind\":\"" + FrontierV3DiagnosticJson.quote(actual.itemKind())
                + "\",\"count\":" + actual.count() + "},\"matchesCanonical\":" + matches + "}";
        return FrontierV3DiagnosticJson.bounded("player_resource", id, checkpoint, value);
    }

    record Expected(SubjectId accountId, UUID playerId, int slot, int canonicalQuantity, SubjectId bindingId,
                    long authorityEpoch, String itemKind, int bindingQuantity, String playerSaveFence) { }

    record Actual(String itemKind, int count) { }
}
