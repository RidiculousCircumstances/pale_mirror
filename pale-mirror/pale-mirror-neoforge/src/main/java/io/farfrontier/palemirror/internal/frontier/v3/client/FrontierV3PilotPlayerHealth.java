package io.farfrontier.palemirror.internal.frontier.v3.client;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
/** Read-only client packet state. This observer cannot cause or confirm a domain damage outcome. */
final class FrontierV3PilotPlayerHealth {
    static boolean reached(Minecraft minecraft,JsonObject action) {
        float health=minecraft.player.getHealth();
        boolean result=(!action.has("minimum") || health>=action.get("minimum").getAsDouble())
                && (!action.has("maximum") || health<=action.get("maximum").getAsDouble());
        if(result)io.farfrontier.palemirror.PaleMirrorMod.LOGGER.info("PMV3_PILOT player_health value={} minimum={} maximum={}",
                health,action.get("minimum"),action.get("maximum"));
        return result;
    }
    private FrontierV3PilotPlayerHealth(){}
}
