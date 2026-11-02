package io.farfrontier.palemirror.internal.frontier.v3.client;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.util.UUID;
/** Read-only binding to the received actor body; a repeated display name cannot substitute another person. */
final class FrontierV3PilotEntityIdentity {
    private static String lastQueryWorker;
    private static long lastQueryTick=Long.MIN_VALUE;
    static boolean matches(Minecraft minecraft,JsonObject action,Entity entity){
        if(!action.has("workerId"))return true;
        String worker=action.get("workerId").getAsString();UUID uuid=expectedEntityUuid(worker);
        long tick=minecraft.player.tickCount;
        if(uuid==null && tick%20==0 && (tick!=lastQueryTick || !worker.equals(lastQueryWorker))){
            lastQueryTick=tick;lastQueryWorker=worker;
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect actor "+worker);
        }
        return uuid!=null && uuid.equals(entity.getUUID());
    }
    static UUID expectedEntityUuid(String worker) {
        var received=worker==null?null:FrontierV3TestPilotClient.diagnostics.get(
                new FrontierV3TestPilotClient.DiagnosticIdentity("actor",worker));
        JsonObject actor=received==null?null:received.value();
        if (actor == null || !actor.has("physicalAdmission") || !actor.get("physicalAdmission").isJsonObject()) return null;
        JsonObject admission = actor.getAsJsonObject("physicalAdmission");
        if (!admission.has("entityUuid") || !admission.get("entityUuid").isJsonPrimitive()) return null;
        try { return UUID.fromString(admission.get("entityUuid").getAsString()); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    private FrontierV3PilotEntityIdentity(){}
}
