package io.farfrontier.palemirror.internal.presentation;

import java.util.function.Function;

import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Player-facing living-region handoff; canonical state remains read-only here. */
public final class CampaignPresentationRuntime {
    private CampaignPresentationRuntime() { }

    public static boolean presentJournal(PaleMirrorSavedData data, ServerPlayer player, BlockPos position,
                                         Function<ServerPlayer, StoryAudienceId> audiences) {
        boolean presented = RegionalJournal.present(data, player, position, audiences);
        if (presented) data.worldState().livingRegions().stream()
                .filter(region -> data.worldRegistry().find(region.placeId()).map(entry -> entry.anchor().equals(position)
                        && entry.dimensionId().equals(player.serverLevel().dimension().location().toString())).orElse(false))
                .findFirst().flatMap(region -> data.worldRegistry().find(region.placeId()))
                .ifPresent(settlement -> CampaignWelcomeKit.grant(player, settlement));
        return presented;
    }

    public static void refreshLedger(PaleMirrorSavedData data, ServerPlayer player, ItemStack stack,
                                     StoryAudienceId audience) {
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, RegionalJournal.ledger(data, player, audience));
        player.getInventory().setChanged();
    }
}
