package io.farfrontier.palemirror.internal.frontier.v3.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/** Ordinary ordered player packets; no server-side or canonical mutation capability. */
final class FrontierV3PilotEntityInteraction {
    private FrontierV3PilotEntityInteraction() { }

    static void interact(Minecraft minecraft, Entity target, boolean secondary) {
        if (!secondary) {
            minecraft.gameMode.interact(minecraft.player, target, InteractionHand.MAIN_HAND);
            return;
        }
        // LocalPlayer reads secondary use from Input, not Entity's shift flag.
        // MultiPlayerGameMode stamps that value into the actual interaction packet.
        boolean previous = minecraft.player.input.shiftKeyDown;
        minecraft.player.input.shiftKeyDown = true;
        minecraft.getConnection().send(new ServerboundPlayerCommandPacket(minecraft.player,
                ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
        try {
            minecraft.gameMode.interact(minecraft.player, target, InteractionHand.MAIN_HAND);
        } finally {
            minecraft.player.input.shiftKeyDown = previous;
            minecraft.getConnection().send(new ServerboundPlayerCommandPacket(minecraft.player,
                    previous ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                            : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        }
    }
}
