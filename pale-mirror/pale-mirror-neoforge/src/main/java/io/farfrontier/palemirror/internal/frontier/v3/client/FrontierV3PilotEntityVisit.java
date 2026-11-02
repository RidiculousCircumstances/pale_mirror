package io.farfrontier.palemirror.internal.frontier.v3.client;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import java.util.Comparator;
/** Setup-only rendezvous with received physical bodies; never derives their pose from old route origins. */
final class FrontierV3PilotEntityVisit {
    private static int targetId=-1;
    private static boolean sent;
    static void reset(){targetId=-1;sent=false;}
    static boolean ready(Minecraft minecraft,JsonObject action){
        var expectedType=ResourceLocation.parse(action.get("entityType").getAsString());
        var expectedName=FrontierV3PilotPresentationName.resolve(minecraft,action);
        if(expectedName==null)return false;
        double maximum=action.has("maxDistance")?action.get("maxDistance").getAsDouble():64.0;
        Entity target=targetId<0?minecraft.level.getEntitiesOfClass(Entity.class,minecraft.player.getBoundingBox().inflate(maximum),
                entity->FrontierV3PilotEntityIdentity.matches(minecraft,action,entity) && entity.isAlive() && !entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(expectedType)
                        && entity.getCustomName()!=null && entity.getCustomName().getString().contains(expectedName)).stream()
                .min(Comparator.comparingDouble((Entity entity)->entity.distanceToSqr(minecraft.player)).thenComparing(Entity::getUUID)).orElse(null)
                :minecraft.level.getEntity(targetId);
        if(target==null)return false;
        if(!sent){
            targetId=target.getId();minecraft.options.keyUp.setDown(false);minecraft.options.keyJump.setDown(false);
            minecraft.player.connection.sendCommand("execute at "+target.getUUID()+" run tp @s ~ ~ ~");sent=true;return false;
        }
        return minecraft.player.onGround() && minecraft.player.distanceToSqr(target)<=6.25 && minecraft.player.hasLineOfSight(target)
                && FrontierV3ClientChunkReadiness.received(minecraft.level,minecraft.player.blockPosition());
    }
    private FrontierV3PilotEntityVisit(){}
}
