package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.internal.network.CombatGesturePayload;
import io.farfrontier.palemirror.internal.network.CombatGesture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native presentation clock only. The retained strike and journal own all gameplay decisions. */
@EventBusSubscriber(modid=PaleMirrorMod.MOD_ID)
public final class FrontierV3CombatPresentation {
    private static final String KEY="pm_v3_combat_windup";
    static long start(ServerLevel level,Mob body,MeleeStrike strike,MeleeRules rules,LivingEntity target){
        return begin(level,body,strike.owner().value(),strike.attemptOrdinal(),strike.attacker().body().physicalEpoch(),CombatGesture.strike(strike.attack()),
            (int)rules.windup(strike.attack()),rules.techniques().require(strike.attack()).activeTicks(),rules.techniques().require(strike.attack()).recoveryTicks(),target);
    }
    static long begin(ServerLevel level,Mob body,String owner,long ordinal,long epoch,CombatGesture gesture,int preparation,int active,int recovery,LivingEntity target){
        var marker=body.getPersistentData().getCompound(KEY);
        if(!marker.getString("owner").equals(owner) || marker.getLong("ordinal")!=ordinal || level.getGameTime()<marker.getLong("startedAtTick")){
            marker=new CompoundTag();marker.putString("owner",owner);marker.putLong("ordinal",ordinal);marker.putLong("epoch",epoch);marker.putInt("attack",gesture.wireTag());
            marker.putInt("preparation",preparation);marker.putInt("active",active);marker.putInt("recovery",recovery);marker.putLong("startedAtTick",level.getGameTime());
            marker.putInt("phase",CombatGesturePayload.Phase.PREPARING.wireTag());facing(marker,body,target);body.getPersistentData().put(KEY,marker);broadcast(level,body,marker);
        }
        return marker.getLong("startedAtTick");
    }
    static void maneuverMoving(ServerLevel level,Mob body,MeleeManeuver m){
        var marker=body.getPersistentData().getCompound(KEY);
        if(!marker.getString("owner").equals(m.owner().value()) || marker.getLong("ordinal")!=m.attemptOrdinal() || marker.getInt("phase")!=CombatGesturePayload.Phase.PREPARING.wireTag())return;
        marker.putLong("startedAtTick",level.getGameTime()-marker.getInt("preparation"));marker.putInt("phase",CombatGesturePayload.Phase.IMPACT.wireTag());body.getPersistentData().put(KEY,marker);broadcast(level,body,marker);
    }
    static void maneuverEnded(ServerLevel level,Mob body,MeleeManeuver m){finish(level,body,m.owner().value(),m.attemptOrdinal(),CombatGesturePayload.Phase.CANCELLED);}
    static void impact(ServerLevel level,Mob body,MeleeStrike strike,LivingEntity target){
        var marker=body.getPersistentData().getCompound(KEY);facing(marker,body,target);body.getPersistentData().put(KEY,marker);
        finish(level,body,strike,CombatGesturePayload.Phase.IMPACT);
    }
    private static void facing(CompoundTag marker,Mob body,LivingEntity target){
        var direction=target.getEyePosition().subtract(body.getEyePosition());
        marker.putFloat("facingYaw",(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
        marker.putFloat("facingPitch",(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
    }
    /** Validation reads the retained clock; it never starts a pose or changes native NBT. */
    static boolean strikeWindow(ServerLevel level,Mob body,MeleeStrike strike,MeleeRules rules){
        var marker=body.getPersistentData().getCompound(KEY);long elapsed=level.getGameTime()-marker.getLong("startedAtTick");
        return marker.getString("owner").equals(strike.owner().value()) && marker.getLong("ordinal")==strike.attemptOrdinal()
                && marker.getLong("epoch")==strike.attacker().body().physicalEpoch()
                && marker.getInt("phase")!=CombatGesturePayload.Phase.CANCELLED.wireTag()
                && elapsed>=rules.windup(strike.attack()) && elapsed<=rules.windup(strike.attack())+rules.techniques().require(strike.attack()).activeTicks();
    }
    static void cancel(ServerLevel level,Mob body,MeleeStrike strike){finish(level,body,strike,CombatGesturePayload.Phase.CANCELLED);}
    private static void finish(ServerLevel level,Mob body,MeleeStrike strike,CombatGesturePayload.Phase phase){
        finish(level,body,strike.owner().value(),strike.attemptOrdinal(),phase);
    }
    private static void finish(ServerLevel level,Mob body,String owner,long ordinal,CombatGesturePayload.Phase phase){
        var marker=body.getPersistentData().getCompound(KEY);
        if(!marker.getString("owner").equals(owner) || marker.getLong("ordinal")!=ordinal || marker.getInt("phase")==CombatGesturePayload.Phase.CANCELLED.wireTag())return;
        marker.putInt("phase",phase.wireTag());body.getPersistentData().put(KEY,marker);broadcast(level,body,marker);
    }
    private static CombatGesturePayload cue(ServerLevel level,Mob body,CompoundTag marker){
        return new CombatGesturePayload(body.getId(),body.getUUID(),marker.getLong("epoch"),marker.getLong("ordinal"),
            CombatGesture.require(marker.getInt("attack")),CombatGesturePayload.Phase.require(marker.getInt("phase")),
            marker.getLong("startedAtTick"),level.getGameTime(),marker.getInt("preparation"),marker.getInt("active"),marker.getInt("recovery"),marker.getFloat("facingYaw"),marker.getFloat("facingPitch"));
    }
    private static void broadcast(ServerLevel level,Mob body,CompoundTag marker){
        var payload=cue(level,body,marker);
        for(var player:level.players())if(player.distanceToSqr(body)<=128*128 && NetworkRegistry.hasChannel(player.connection,CombatGesturePayload.TYPE.id()))
            PacketDistributor.sendToPlayer(player,payload);
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){
        if(!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Mob body)
                || !(body.level() instanceof ServerLevel level) || !body.isAlive())return;
        var marker=body.getPersistentData().getCompound(KEY);
        if(!marker.contains("attack") || marker.getLong("epoch")<1)return;
        long age=level.getGameTime()-marker.getLong("startedAtTick");
        if(age<0 || age>=marker.getInt("preparation")+marker.getInt("active")+marker.getInt("recovery")+10)return;
        if(NetworkRegistry.hasChannel(player.connection,CombatGesturePayload.TYPE.id()))PacketDistributor.sendToPlayer(player,cue(level,body,marker));
    }
    private FrontierV3CombatPresentation(){}
}
