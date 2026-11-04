package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.*;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Console-selected, read-only combat flight recorder. Never settles runtime or requests physical demand. */
@EventBusSubscriber(modid=PaleMirrorMod.MOD_ID)
public final class FrontierV3CombatTrace {
    private static final int RADIUS=48, MAX_BODIES=64;
    private static final Map<MinecraftServer,Session> SESSIONS=new WeakHashMap<>();
    private static final class Session {
        final UUID player; final int interval; final CombatTraceCapture capture;
        final Set<UUID> tracked=new HashSet<>();
        final Map<String,String> changes=new LinkedHashMap<>();
        long nextSample; boolean closing;
        Session(UUID player,int interval,CombatTraceCapture capture){this.player=player;this.interval=interval;this.capture=capture;tracked.add(player);}
    }
    static String start(MinecraftServer server,ServerPlayer player,int interval,int seconds) {
        if(interval<1 || interval>20)throw new IllegalArgumentException("position interval must be 1..20 ticks");
        var prior=SESSIONS.get(server);
        if(prior!=null && !prior.capture.finished())throw new IllegalStateException("combat capture already active or exporting; use status/stop");
        var header=new JsonObject();header.addProperty("player",player.getUUID().toString());header.addProperty("playerName",player.getGameProfile().getName());
        header.addProperty("positionIntervalTicks",interval);header.addProperty("radiusBlocks",RADIUS);header.addProperty("maxBodies",MAX_BODIES);
        header.addProperty("worldDirectory",server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString());header.addProperty("pid",ProcessHandle.current().pid());
        header.addProperty("seed",player.serverLevel().getSeed());header.addProperty("evidenceOnly",true);
        var capture=new CombatTraceCapture(server.getServerDirectory().resolve("debug/pale-mirror/combat"),seconds,header);
        SESSIONS.put(server,new Session(player.getUUID(),interval,capture));
        capture.readiness().whenComplete((unused,failure)->{
            if(failure==null)PaleMirrorMod.LOGGER.info("PMV3_COMBAT_CAPTURE_READY capture={} file={}",capture.id,capture.json);
            else PaleMirrorMod.LOGGER.error("PMV3_COMBAT_CAPTURE_OPEN_FAILED capture={}",capture.id,failure);
        });
        return capture.status();
    }
    static String stop(MinecraftServer server,String reason) {
        var session=SESSIONS.get(server);if(session==null)return "No combat capture";
        finish(session,reason);return session.capture.status();
    }
    static String status(MinecraftServer server) {var session=SESSIONS.get(server);return session==null?"No combat capture":session.capture.status();}
    private static void finish(Session session,String reason) {
        if(session.closing)return;
        session.closing=true;session.capture.finish(reason);session.tracked.clear();session.changes.clear();
        session.capture.completion().whenComplete((path,failure)->{
            if(failure==null)PaleMirrorMod.LOGGER.info("PMV3_COMBAT_CAPTURE_SAVED capture={} file={}",session.capture.id,path);
            else PaleMirrorMod.LOGGER.error("PMV3_COMBAT_CAPTURE_SAVE_FAILED capture={}",session.capture.id,failure);
        });
        PaleMirrorMod.LOGGER.info("PMV3_COMBAT_CAPTURE_END capture={} reason={} file={}",session.capture.id,reason,session.capture.json);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();var session=SESSIONS.get(server);if(session==null || session.closing)return;
        if(session.capture.full() || session.capture.expired()) {finish(session,session.capture.full()?"EVENT_LIMIT":"DURATION");return;}
        if(session.capture.finished()){finish(session,"OPEN_FAILED");return;}
        if(!session.capture.running() || server.getTickCount()<session.nextSample)return;
        session.nextSample=server.getTickCount()+session.interval;
        var player=server.getPlayerList().getPlayer(session.player);if(player==null){finish(session,"PLAYER_DISCONNECTED");return;}
        try {
            var level=player.serverLevel();var nearby=level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(RADIUS),body->body!=player);
            var frame=base(level);frame.addProperty("nearbyBodies",nearby.size());frame.addProperty("bodyLimitReached",nearby.size()>MAX_BODIES-1);
            var selected=new ArrayList<LivingEntity>();selected.add(player);
            nearby.stream().sorted(Comparator.comparingDouble(player::distanceToSqr)).limit(MAX_BODIES-1).forEach(selected::add);
            frame.addProperty("bodyCount",selected.size());record(session,"frame",frame);
            for(var entity:selected) {
                if(session.tracked.size()<128)session.tracked.add(entity.getUUID());
                var sample=frame.deepCopy();sample.add("body",body(entity));record(session,"position",sample);
            }
        } catch(RuntimeException failure) {failed(session,failure);}
    }
    @SubscribeEvent public static void shutdown(ServerStoppingEvent event) {var session=SESSIONS.get(event.getServer());if(session!=null)finish(session,"SERVER_STOP");}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        var session=SESSIONS.remove(event.getServer());
        if(session!=null)PaleMirrorMod.LOGGER.info("PMV3_COMBAT_CAPTURE_SHUTDOWN {}",session.capture.awaitShutdown());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void attackInput(AttackEntityEvent event) {
        if(event.getEntity().level() instanceof ServerLevel level) nativeEvent(level,"player_attack_input",event.getEntity(),event.getTarget(),
                event.isCanceled()?"CANCELLED":"REQUESTED",0);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void incoming(LivingIncomingDamageEvent event) {
        if(event.getEntity().level() instanceof ServerLevel level) nativeEvent(level,"damage_incoming",event.getSource().getEntity(),event.getEntity(),
                event.isCanceled()?"CANCELLED":event.getSource().getMsgId(),event.getAmount());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Post event) {
        if(event.getEntity().level() instanceof ServerLevel level) nativeEvent(level,"damage_applied",event.getSource().getEntity(),event.getEntity(),
                event.getSource().getMsgId(),event.getNewDamage());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void death(LivingDeathEvent event) {
        if(event.getEntity().level() instanceof ServerLevel level) nativeEvent(level,"death",event.getSource().getEntity(),event.getEntity(),
                event.isCanceled()?"CANCELLED":event.getSource().getMsgId(),0);
    }
    private static Session interested(ServerLevel level,Entity a,Entity b) {
        var session=SESSIONS.get(level.getServer());if(session==null || !session.capture.running())return null;
        if(a!=null && session.tracked.contains(a.getUUID()) || b!=null && session.tracked.contains(b.getUUID()))return session;
        var focus=level.getServer().getPlayerList().getPlayer(session.player);
        return focus!=null && focus.level()==level && (a!=null && a.distanceToSqr(focus)<=RADIUS*RADIUS || b!=null && b.distanceToSqr(focus)<=RADIUS*RADIUS)?session:null;
    }
    private static void nativeEvent(ServerLevel level,String kind,Entity source,Entity target,String result,float amount) {
        var session=interested(level,source,target);if(session==null)return;
        try {
            var row=base(level);row.add("source",body(source));row.add("target",body(target));row.addProperty("result",result);row.addProperty("damage",amount);
            record(session,kind,row);
        } catch(RuntimeException failure) {failed(session,failure);}
    }
    static void unavailable(ServerLevel level,CombatOrder order,String reason) {
        var session=SESSIONS.get(level.getServer());
        if(session==null || !session.capture.running() || !session.tracked.contains(order.target().entity())
                || !changed(session,order.owner().value()+":unavailable",reason))return;
        try {
            var row=base(level);row.addProperty("actor",order.execution().actorId().value());row.addProperty("owner",order.owner().value());
            row.addProperty("target",order.target().entity().toString());row.addProperty("gate",reason);record(session,"unavailable",row);
        } catch(RuntimeException failure) {failed(session,failure);}
    }
    static void decision(ServerLevel level,Mob fighter,LivingEntity target,CombatOrder order,MeleeAttack attack,String gate,
            double distance,double separating,double lateral,boolean canSpend) {
        var session=interested(level,fighter,target);if(session==null)return;
        String key=fighter.getUUID()+":decision",value=target.getUUID()+":"+order.revision()+":"+attack+":"+gate+":"+canSpend;
        if(!changed(session,key,value))return;
        try {
            var row=base(level);row.add("fighter",body(fighter));row.add("target",body(target));row.addProperty("owner",order.owner().value());
            row.addProperty("attack",attack.name());row.addProperty("gate",gate);row.addProperty("distance",distance);
            row.addProperty("separatingSpeed",separating);row.addProperty("lateralSpeed",lateral);row.addProperty("canSpend",canSpend);row.addProperty("orderRevision",order.revision());
            var runtime=FrontierV3ServerLifecycle.runtimeFor(level.getServer());var state=runtime==null?null:runtime.decodedState().orElse(null);
            if(state!=null) {
                var plan=state.combat().engagements().get(CombatEngagement.Key.of(order));
                if(plan!=null)plan.wave().ifPresent(wave->{
                    var slots=new JsonArray();for(var slot:wave.slots()) {
                        var valueRow=new JsonObject();valueRow.addProperty("actor",slot.execution().actorId().value());
                        valueRow.addProperty("opensAt",slot.opensAt());valueRow.addProperty("closesAt",slot.closesAt());valueRow.addProperty("committed",slot.committed());slots.add(valueRow);
                    }
                    row.add("waveSlots",slots);
                });
            }
            record(session,"decision",row);
        } catch(RuntimeException failure) {failed(session,failure);}
    }
    static void strike(ServerLevel level,Mob fighter,LivingEntity target,MeleeStrike strike,String stage,String detail) {
        var session=interested(level,fighter,target);if(session==null)return;
        if(!changed(session,fighter.getUUID()+":strike:"+stage,strike.attemptOrdinal()+":"+stage+":"+detail))return;
        try {
            var row=base(level);row.add("fighter",body(fighter));row.add("target",body(target));row.addProperty("owner",strike.owner().value());
            row.addProperty("attempt",strike.attemptOrdinal());row.addProperty("preparedAt",strike.preparedAt());row.addProperty("attack",strike.attack().name());
            row.addProperty("weaponReach",strike.profile().reach());row.addProperty("weaponBaseDamage",strike.profile().damage());
            row.addProperty("weaponIntervalTicks",strike.profile().attackIntervalTicks());row.addProperty("stage",stage);row.addProperty("detail",detail);
            row.addProperty("distanceToHitbox",Math.sqrt(target.getBoundingBox().distanceToSqr(fighter.getEyePosition())));
            row.addProperty("lineOfSight",fighter.hasLineOfSight(target));
            var cue=fighter.getPersistentData().getCompound("pm_v3_combat_windup");
            if(cue.contains("startedAtTick")) {
                row.addProperty("cueStartedAt",cue.getLong("startedAtTick"));row.addProperty("aimYaw",cue.getFloat("facingYaw"));
                row.addProperty("cuePhase",cue.getInt("phase"));
                double relative=Math.toRadians(Math.toDegrees(Math.atan2(-(target.getX()-fighter.getX()),target.getZ()-fighter.getZ()))-cue.getFloat("facingYaw"));
                row.addProperty("angleFromAimDegrees",Math.toDegrees(Math.atan2(Math.sin(relative),Math.cos(relative))));
            }
            record(session,"strike",row);
        } catch(RuntimeException failure) {failed(session,failure);}
    }
    static void gesture(ServerLevel level,Mob fighter,LivingEntity target,String gesture,long ordinal,int preparation,int active,int recovery) {
        var session=interested(level,fighter,target);if(session==null)return;
        try {
            var row=base(level);row.add("fighter",body(fighter));row.add("target",body(target));row.addProperty("gesture",gesture);row.addProperty("attempt",ordinal);
            row.addProperty("preparation",preparation);row.addProperty("active",active);row.addProperty("recovery",recovery);record(session,"gesture_start",row);
        } catch(RuntimeException failure) {failed(session,failure);}
    }
    private static boolean changed(Session session,String key,String value) {
        if(value.equals(session.changes.get(key)))return false;
        if(session.changes.size()>=512 && !session.changes.containsKey(key))session.changes.remove(session.changes.keySet().iterator().next());
        session.changes.put(key,value);return true;
    }
    private static JsonObject base(ServerLevel level) {
        var row=new JsonObject();row.addProperty("serverTick",level.getServer().getTickCount());row.addProperty("worldTick",level.getGameTime());
        row.addProperty("dimension",level.dimension().location().toString());
        var runtime=FrontierV3ServerLifecycle.runtimeFor(level.getServer());row.addProperty("simulationTick",runtime==null?-1:runtime.calendarInstant().orElse(-1));return row;
    }
    private static JsonElement body(Entity entity) {
        if(entity==null)return JsonNull.INSTANCE;
        var row=new JsonObject();row.addProperty("uuid",entity.getUUID().toString());row.addProperty("entityId",entity.getId());
        row.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());row.addProperty("name",entity.getName().getString());
        row.addProperty("x",entity.getX());row.addProperty("y",entity.getY());row.addProperty("z",entity.getZ());
        var velocity=entity.getDeltaMovement();row.addProperty("vx",velocity.x);row.addProperty("vy",velocity.y);row.addProperty("vz",velocity.z);
        row.addProperty("yaw",entity.getYRot());row.addProperty("pitch",entity.getXRot());row.addProperty("grounded",entity.onGround());
        row.addProperty("alive",entity.isAlive());row.addProperty("sprinting",entity.isSprinting());row.addProperty("horizontalCollision",entity.horizontalCollision);
        if(entity instanceof LivingEntity living) {
            row.addProperty("health",living.getHealth());row.addProperty("hurtTime",living.hurtTime);row.addProperty("invulnerableTime",living.invulnerableTime);
            row.addProperty("blocking",living.isBlocking());row.addProperty("swinging",living.swinging);row.addProperty("armour",living.getArmorValue());
            row.addProperty("weapon",BuiltInRegistries.ITEM.getKey(living.getMainHandItem().getItem()).toString());
            row.addProperty("weaponDamage",living.getMainHandItem().getDamageValue());
        }
        if(entity instanceof ServerPlayer player) {
            row.addProperty("gameMode",player.gameMode.getGameModeForPlayer().getName());row.addProperty("attackStrength",player.getAttackStrengthScale(0));
        }
        if(entity instanceof Mob mob) {
            row.addProperty("navigationDone",mob.getNavigation().isDone());var goal=mob.getNavigation().getTargetPos();
            if(goal!=null){row.addProperty("navigationX",goal.getX());row.addProperty("navigationY",goal.getY());row.addProperty("navigationZ",goal.getZ());}
        }
        var binding=FrontierV3ActorOwnerBinding.from(entity);
        if(binding.isPresent() && entity.level() instanceof ServerLevel level) {
            var declaration=binding.orElseThrow().declaration();row.addProperty("actor",declaration.actorId().value());row.addProperty("bodyEpoch",declaration.epoch());
            var runtime=FrontierV3ServerLifecycle.runtimeFor(level.getServer());var state=runtime==null?null:runtime.decodedState().orElse(null);
            if(state!=null) {
                var execution=state.actorExecutions().actors().get(declaration.actorId());
                if(execution!=null)execution.current().ifPresent(current->{
                    row.addProperty("activity",current.activityKind().name());row.addProperty("owner",current.activityOwnerId().value());
                    var owner=current.activityOwnerId();var combat=state.combat();
                    if(combat.armaments().containsKey(owner)) {
                        row.addProperty("readiness",CombatReadiness.available(combat.readiness().get(owner),runtime.calendarInstant().orElseThrow(),state.bootstrap().ruleset().melee().readiness()));
                        row.addProperty("nextAttackAt",combat.nextAttack().getOrDefault(owner,0L));
                    }
                    var pending=combat.pending().get(owner);
                    if(pending!=null && pending.attacker().execution().equals(current)) {
                        row.addProperty("pendingAttack",pending.attack().name());row.addProperty("attempt",pending.attemptOrdinal());
                        row.addProperty("preparedAt",pending.preparedAt());row.addProperty("impactAt",pending.impactAt(state.bootstrap().ruleset().melee()));
                    }
                });
            }
        }
        return row;
    }
    private static void record(Session session,String kind,JsonObject row) {session.capture.record(kind,row);}
    private static void failed(Session session,RuntimeException failure) {
        PaleMirrorMod.LOGGER.error("PMV3_COMBAT_CAPTURE_FAILED capture={}",session.capture.id,failure);finish(session,"CAPTURE_ERROR");
    }
    private FrontierV3CombatTrace() { }
}
