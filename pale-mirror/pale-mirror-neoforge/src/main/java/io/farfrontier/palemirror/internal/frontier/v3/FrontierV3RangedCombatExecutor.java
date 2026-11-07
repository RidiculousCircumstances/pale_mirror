package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.*;
import java.util.*;
/** Native ranged adapter: sight, visible reload/aim and an exactly debited vanilla projectile emission. No locomotion writer. */
final class FrontierV3RangedCombatExecutor {
    private static final String CLOCK="pm_v3_crossbow_preparation",EMISSION="pm_v3_crossbow_emission";
    private static final Map<ServerLevel,Long> NEXT=new WeakHashMap<>();
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime){
        var state=runtime.decodedState().orElseThrow();
        for(var shot:state.combat().ranged().pending().values().stream().limit(32).toList())execute(level,runtime,shot);
        if(level.getGameTime()<NEXT.getOrDefault(level,0L))return;
        NEXT.put(level,level.getGameTime()+state.bootstrap().ruleset().ranged().sensingTicks());
        long tick=runtime.executionView().orElseThrow().instant().ticks();
        for(var nominated:CombatComposition.CAPABILITIES.orders(state,tick)){
            if(nominated.method()!=CombatMethod.CROSSBOW)continue;
            state=runtime.decodedState().orElseThrow();var order=CombatComposition.CAPABILITIES.current(state,nominated.execution(),tick).orElse(null);if(order==null)continue;
            var entity=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),order.execution().actorId()));
            var other=level.getEntity(order.target().entity());
            if(!(entity instanceof Mob fighter) || !(other instanceof LivingEntity target) || !fighter.isAlive() || !target.isAlive()
                    || target instanceof ServerPlayer player&&(player.isCreative()||player.isSpectator()))continue;
            FrontierV3ActorActuation permission;
            try{permission=FrontierV3ActorActuation.capture(state,fighter,order.execution(),runtime::decodedState);}catch(IllegalArgumentException stale){continue;}
            if(!FrontierV3ActorBodyController.inspectCurrent(level,runtime,fighter)||!permission.current(fighter))continue;
            state=runtime.decodedState().orElseThrow();order=CombatComposition.CAPABILITIES.current(state,order.execution(),tick).orElse(null);if(order==null)continue;
            var use=(CombatUse.Crossbow)order.use();var rules=state.bootstrap().ruleset().ranged();
            boolean visible=fighter.hasLineOfSight(target),clear=visible&&clearShot(level,fighter,target,rules);
            var sight=new RangedStance(permission.id(),order.target(),order.targetPosition(),visible,clear,tick);
            FrontierV3CommandSubmission.propose(runtime,"ranged-sight",order.owner().value(),new RangedEvent.Observed(sight));
            fighter.getLookControl().setLookAt(target,60,60);
            if(!visible||!clear || state.actorMovements().containsKey(order.execution().actorId()) || state.combat().ranged().unsettled(order.owner())
                    || tick<state.combat().ranged().nextAttack().getOrDefault(order.owner(),0L) || !inRange(fighter,target,rules))continue;
            var item=state.inventory().items().get(order.weapon());
            if(item==null || !(fighter.getMainHandItem().getItem() instanceof CrossbowItem) || !FrontierV3ExactItemPresentation.exactMatch(fighter.getMainHandItem(),item))continue;
            var ammo=Ammunition.spendable(state,order.execution().actorId(),item.economicOwnerId(),use.ammunition());if(ammo.isEmpty())continue;
            var b=ammo.orElseThrow();var slot=slot(b.address());var actual=FrontierV3ActorResourceSlots.get(fighter,slot);
            if(actual.getCount()!=b.quantity()||!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(use.ammunition()))continue;
            var shot=new RangedShot(permission.id(),order.method(),order.revision(),order.weapon(),item.condition(),order.target(),targetPort(target).identity().incarnation(),
                    b.accountId(),b.lotQuantities().keySet().stream().min(Comparator.naturalOrder()).orElseThrow(),use.ammunition(),b,tick+1,tick,order.profile());
            FrontierV3CommandSubmission.propose(runtime,"ranged-prepare",order.owner().value(),new RangedEvent.Prepared(shot));
        }
    }
    private static void execute(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,RangedShot shot){
        var state=runtime.decodedState().orElseThrow();var ledger=FrontierV3OperationLedger.get(level);
        var preparation=new RangedEvent.Prepared(shot);String key=FrontierV3OperationConfirmation.fingerprint(preparation);
        var old=ledger.find(FrontierV3OperationReceipt.Protocol.RANGED_SHOT,shot.owner(),key);
        if(old.isPresent()){
            var retained=old.orElseThrow();var command=ledger.ownerCommand(retained.ticket());
            if(command.isPresent()){FrontierV3OperationConfirmation.submitRequired(level,runtime,retained.ticket(),command.orElseThrow().consequence());return;}
            if(retained.cancelled()){FrontierV3OperationConfirmation.submitRequired(level,runtime,retained.ticket(),new RangedEvent.Completed(shot.owner(),shot.attemptOrdinal(),false,List.of()));return;}
        }
        var entity=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),shot.attacker().execution().actorId()));
        if(!(entity instanceof Mob fighter)){
            var unused=FrontierV3MinecraftOperation.cancelUnusedPreparation(level,FrontierV3OperationReceipt.Protocol.RANGED_SHOT,shot.owner(),preparation);
            if(unused.isPresent()){
                var payload=new RangedEvent.Completed(shot.owner(),shot.attemptOrdinal(),false,List.of());
                if(unused.orElseThrow().cancelledTicket().isPresent())FrontierV3OperationConfirmation.submitRequired(level,runtime,unused.orElseThrow().cancelledTicket().orElseThrow(),payload);
                else FrontierV3CommandSubmission.submit(runtime,"ranged-unattempted",shot.owner().value(),payload);
            }return;
        }
        var other=level.getEntity(shot.target().entity());var target=other instanceof LivingEntity living?living:null;
        var rules=state.bootstrap().ruleset().ranged();var ammoSlot=slot(shot.binding().address());
        var participants=List.of(FrontierV3OperationEntityParticipant.current(fighter,shot.attacker().body().physicalEpoch()));
        var declaration=FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.RANGED_SHOT,shot.owner(),preparation,participants);
        var operation=FrontierV3MinecraftOperation.applyDeferred(level,declaration,participants,
                ()->permitted(runtime,shot,fighter,target)&&ammunitionBefore(fighter,shot),
                new FrontierV3NativeEffect(()->prepare(level,fighter,target,shot,rules),()->{
                    return emit(level,fighter,target,shot,rules,ammoSlot);
                }),()->emitted(fighter,shot));
        if(operation.pending())return;
        if(operation.complete()){
            var after=shot.binding().quantity()==1?List.<FungiblePhysicalObservation.Stack>of():List.of(new FungiblePhysicalObservation.Stack(shot.binding().address(),shot.ammunition(),shot.binding().quantity()-1));
            FrontierV3OperationConfirmation.submit(level,runtime,operation.ticket(),new RangedEvent.Completed(shot.owner(),shot.attemptOrdinal(),true,after),
                    ()->FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(),shot.attacker().execution().actorId(),fighter));
        }else if(operation.status()==FrontierV3MinecraftOperation.Status.NOT_APPLIED||operation.status()==FrontierV3MinecraftOperation.Status.SOURCE_CHANGED){
            clearUnlaunchedCharge(fighter,shot.weapon());
            FrontierV3OperationConfirmation.submitRequired(level,runtime,operation.ticket(),new RangedEvent.Completed(shot.owner(),shot.attemptOrdinal(),false,List.of()));
        }
    }
    static void clearUnlaunchedCharge(Mob fighter,SubjectId weapon){
        fighter.stopUsingItem();
        var held=fighter.getMainHandItem();
        if(held.getItem() instanceof CrossbowItem && FrontierV3ExactItemPresentation.itemId(held).equals(Optional.of(weapon)))
            held.set(DataComponents.CHARGED_PROJECTILES,ChargedProjectiles.EMPTY);
        fighter.getPersistentData().remove(CLOCK);
    }
    static FrontierV3NativeEffect.Outcome emit(ServerLevel level,Mob fighter,LivingEntity target,RangedShot shot,RangedRules rules,ActorItemSlot ammoSlot){
                    var ammo=FrontierV3ActorResourceSlots.get(fighter,ammoSlot);var weapon=fighter.getMainHandItem();
                    if(!(ammo.getItem() instanceof ArrowItem arrowItem)||!(weapon.getItem() instanceof CrossbowItem))return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
                    var projectile=arrowItem.createArrow(level,ammo.copyWithCount(1),fighter,weapon);projectile.setUUID(shot.projectileId());
                    var motion=FrontierV3CombatTargetMotion.observe(fighter,target,shot.attacker(),true,rules.sensingTicks());
                    var aim=aim(fighter,target,rules,new Vec3(motion.x(),0,motion.z()));projectile.shoot(aim.x,aim.y,aim.z,rules.projectileSpeedMicros()/1000000f,RangedMechanics.inaccuracy(shot.actorProfile(),rules));
                    projectile.setSoundEvent(SoundEvents.CROSSBOW_HIT);projectile.pickup=AbstractArrow.Pickup.DISALLOWED;
                    // The launch is a transient vanilla effect. Its actor-side emission witness prevents replay after reload/restart, even after the projectile has expired.
                    if(!level.addFreshEntity(projectile))return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
                    FrontierV3ActorResourceSlots.set(fighter,ammoSlot,ammo.getCount()==1?ItemStack.EMPTY:ammo.copyWithCount(ammo.getCount()-1));
                    weapon.setDamageValue(shot.before().damage()+1);weapon.set(DataComponents.CHARGED_PROJECTILES,ChargedProjectiles.EMPTY);
                    fighter.stopUsingItem();fighter.swing(InteractionHand.MAIN_HAND);
                    var receipt=new CompoundTag();receipt.putString("owner",shot.owner().value());receipt.putLong("ordinal",shot.attemptOrdinal());receipt.putUUID("projectile",shot.projectileId());
                    fighter.getPersistentData().put(EMISSION,receipt);fighter.getPersistentData().remove(CLOCK);
                    level.playSound(null,fighter.getX(),fighter.getY(),fighter.getZ(),SoundEvents.CROSSBOW_SHOOT,SoundSource.HOSTILE,1f,1f);
                    return FrontierV3NativeEffect.Outcome.APPLIED;
    }
    static boolean emitted(Mob actor,RangedShot shot){var marker=actor.getPersistentData().getCompound(EMISSION);
            return marker.getString("owner").equals(shot.owner().value())&&marker.getLong("ordinal")==shot.attemptOrdinal()&&marker.hasUUID("projectile")&&marker.getUUID("projectile").equals(shot.projectileId());}
    private static boolean permitted(FrontierV3ServerRuntime<FrontierWorldState,FrontierWorldProjection> runtime,RangedShot shot,Mob actor,LivingEntity target){
        var s=runtime.decodedState().orElseThrow();long tick=runtime.executionView().orElseThrow().instant().ticks();
        if(!shot.equals(s.combat().ranged().pending().get(shot.owner()))||target==null||!target.isAlive()||!actor.isAlive()||target instanceof ServerPlayer p&&(p.isCreative()||p.isSpectator()))return false;
        try{ActorBodyAuthority.requireActuation(s,shot.attacker());}catch(IllegalArgumentException stale){return false;}
        return targetPort(target).identity().incarnation()==shot.targetIncarnation() && CombatComposition.CAPABILITIES.current(s,shot.attacker().execution(),tick)
                .filter(o->o.method()==shot.method()&&o.revision()==shot.ownerRevision()&&o.weapon().equals(shot.weapon())&&o.target().equals(shot.target())).isPresent()
                && inRange(actor,target,s.bootstrap().ruleset().ranged())&&clearShot((ServerLevel)actor.level(),actor,target,s.bootstrap().ruleset().ranged())
                && s.inventory().items().containsKey(shot.weapon())
                && FrontierV3ExactItemPresentation.exactMatch(actor.getMainHandItem(),s.inventory().items().get(shot.weapon()));
    }
    private static FrontierV3NativeEffect.Admission prepare(ServerLevel level,Mob actor,LivingEntity target,RangedShot shot,RangedRules rules){
        var marker=actor.getPersistentData().getCompound(CLOCK);
        if(!marker.getString("owner").equals(shot.owner().value())||marker.getLong("ordinal")!=shot.attemptOrdinal()){
            marker=new CompoundTag();marker.putString("owner",shot.owner().value());marker.putLong("ordinal",shot.attemptOrdinal());marker.putLong("start",level.getGameTime());actor.getPersistentData().put(CLOCK,marker);
            actor.startUsingItem(InteractionHand.MAIN_HAND);level.playSound(null,actor.getX(),actor.getY(),actor.getZ(),SoundEvents.CROSSBOW_LOADING_START,SoundSource.HOSTILE,1f,1f);
        }
        FrontierV3CombatTargetMotion.observe(actor,target,shot.attacker(),true,rules.sensingTicks());
        actor.getLookControl().setLookAt(target,60,60);long elapsed=level.getGameTime()-marker.getLong("start");
        if(elapsed<RangedMechanics.reloadTicks(shot.actorProfile(),rules))return FrontierV3NativeEffect.Admission.waiting("crossbow-reload");
        actor.stopUsingItem();actor.getMainHandItem().set(DataComponents.CHARGED_PROJECTILES,ChargedProjectiles.of(FrontierV3ActorResourceSlots.get(actor,slot(shot.binding().address())).copyWithCount(1)));
        return elapsed<RangedMechanics.reloadTicks(shot.actorProfile(),rules)+rules.aimTicks()?FrontierV3NativeEffect.Admission.waiting("crossbow-aim"):FrontierV3NativeEffect.Admission.READY;
    }
    private static boolean ammunitionBefore(Mob actor,RangedShot shot){
        var actual=FrontierV3ActorResourceSlots.get(actor,slot(shot.binding().address()));
        var expected=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(shot.ammunition()));
        return actual.getCount()==shot.binding().quantity() && actual.getItem() instanceof ArrowItem && ItemStack.isSameItemSameComponents(actual,new ItemStack(expected));
    }
    static ActorItemSlot slot(PhysicalStackAddress address){return switch(address){case PhysicalStackAddress.ActorPocket p->new ActorItemSlot.Pocket(p.slot());case PhysicalStackAddress.ActorHand h->new ActorItemSlot.Hand(h.hand());
            default->throw new IllegalArgumentException("ammunition has no actor inventory address");};}
    static boolean inRange(Mob actor,LivingEntity target,RangedRules rules){double d=actor.distanceTo(target);return d>=rules.minimumRange()&&d<=rules.maximumRange();}
    static Vec3 aim(Mob actor,LivingEntity target,RangedRules rules){return aim(actor,target,rules,target.getDeltaMovement());}
    static Vec3 aim(Mob actor,LivingEntity target,RangedRules rules,Vec3 velocity){
        var from=actor.getEyePosition().add(0,-0.1,0);double speed=rules.projectileSpeedMicros()/1000000.0;
        double flight=from.distanceTo(target.getBoundingBox().getCenter())/speed;
        var lead=velocity.multiply(1,0,1).scale(Math.min(6,flight));
        var delta=target.getBoundingBox().getCenter().add(lead).subtract(from);
        // Low ballistic arc for the actual launch speed. The vanilla mob's fixed rise coefficient belongs to its slower projectile.
        double horizontal=Math.hypot(delta.x,delta.z),g=0.05,v2=speed*speed;
        double root=v2*v2-g*(g*horizontal*horizontal+2*delta.y*v2);
        double rise=root<=0 || horizontal<0.001?delta.y:(v2-Math.sqrt(root))/g;
        return new Vec3(delta.x,rise,delta.z);
    }
    static boolean clearShot(ServerLevel level,Mob actor,LivingEntity target,RangedRules rules){
        if(!actor.hasLineOfSight(target))return false;var from=actor.getEyePosition();var to=target.getBoundingBox().getCenter();
        if(level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor)).getType()!=HitResult.Type.MISS)return false;
        double margin=rules.friendlyClearanceMicros()/1000000.0;
        return level.getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(margin),e->e!=actor&&e!=target&&e.isAlive()).stream()
                .noneMatch(ally->ally.getBoundingBox().inflate(margin).clip(from,to).isPresent());
    }
    private static FrontierV3MinecraftOperation.Participant targetPort(LivingEntity target){return target instanceof ServerPlayer p?FrontierV3OperationPlayers.currentHealth(p):FrontierV3OperationEntityParticipant.nativeObject(target);}
    private FrontierV3RangedCombatExecutor(){}
}
