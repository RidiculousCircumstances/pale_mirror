package io.farfrontier.palemirror.internal.world;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Shared fail-closed eligibility rule for facts that represent an active story audience. */
public final class PlayerAudienceEligibility {
    private PlayerAudienceEligibility() { }

    public static boolean participates(ServerPlayer player) {
        return !player.isSpectator() && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR;
    }
}
