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
    private static long retryAt;
    static void reset(){targetId=-1;sent=false;retryAt=0;}
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
        boolean arrived=minecraft.player.onGround() && minecraft.player.distanceToSqr(target)<=6.25 && minecraft.player.hasLineOfSight(target)
                && FrontierV3ClientChunkReadiness.received(minecraft.level,minecraft.player.blockPosition());
        if(sent && arrived)return true;
        // The body can keep walking while the player's ordinary teleport/chunk packets settle.
        // Setup may renew its player-only rendezvous; never pin or move the resident.
        if(sent && minecraft.level.getGameTime()>=retryAt)sent=false;
        if(!sent){
            targetId=target.getId();minecraft.options.keyUp.setDown(false);minecraft.options.keyJump.setDown(false);
            double[] offset=standOff(minecraft,target,action.has("standOff")?action.get("standOff").getAsDouble():0);
            if(offset==null)return false;
            minecraft.player.connection.sendCommand("execute at "+target.getUUID()+" run tp @s ~"+offset[0]+" ~"+offset[1]+" ~"+offset[2]);sent=true;retryAt=minecraft.level.getGameTime()+20;return false;
        }
        return false;
    }
    /** Setup chooses a real collision-supported player contact, never a resident pose or canonical result. */
    private static double[] standOff(Minecraft minecraft,Entity target,double distance){
        if(distance==0)return new double[]{0,0,0};
        for(int direction=0;direction<8;direction++)for(int dy:new int[]{0,-1,1}){
            double dx=Math.cos(direction*Math.PI/4)*distance,dz=Math.sin(direction*Math.PI/4)*distance;
            var point=target.position().add(dx,dy,dz);var box=minecraft.player.getBoundingBox().move(point.subtract(minecraft.player.position()));
            if(minecraft.level.noCollision(minecraft.player,box) && !minecraft.level.noCollision(minecraft.player,box.move(0,-0.1,0)))return new double[]{dx,dy,dz};
        }
        return null;
    }
    private FrontierV3PilotEntityVisit(){}
}
