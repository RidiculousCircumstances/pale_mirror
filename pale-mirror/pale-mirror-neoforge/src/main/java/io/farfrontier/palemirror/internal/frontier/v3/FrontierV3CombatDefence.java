package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.*;
import java.util.Optional;
/** Finite common defence over a published actor permission; no species or appointment dependency. */
@EventBusSubscriber(modid=PaleMirrorMod.MOD_ID)
public final class FrontierV3CombatDefence {
    record Context(FrontierWorldState state,CombatOrder order,long tick){}
    static Optional<Context> context(LivingEntity body){
        if(!(body.level() instanceof ServerLevel level))return Optional.empty();
        var runtime=FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if(runtime==null || runtime.status().kind()!=FrontierV3RuntimeStatus.Kind.ACTIVE)return Optional.empty();
        var declaration=FrontierV3ActorCarrierComposition.declaredBy(body).orElse(null);
        if(declaration==null || declaration.owner()!=FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY
                || declaration.representation()!=FrontierV3ActorCarrierComposition.Representation.LIVE_BODY)return Optional.empty();
        var state=runtime.decodedState().orElseThrow();var slot=state.actorExecutions().actors().get(declaration.actorId());
        if(slot==null || slot.current().isEmpty())return Optional.empty();var execution=slot.current().orElseThrow();
        if(!CombatComposition.CAPABILITIES.supports(execution.activityKind()) || !ActorBodyAuthority.retainsPhysicalCustody(state,declaration.actorId())
                || ActorBodyAuthority.current(state,declaration.actorId()).physicalEpoch()!=declaration.epoch())return Optional.empty();
        if(!body.getUUID().equals(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(),declaration.actorId())))return Optional.empty();
        long tick=runtime.executionView().orElseThrow().instant().ticks();
        return CombatComposition.CAPABILITIES.current(state,execution,tick).map(order->new Context(state,order,tick));
    }
    static boolean blocking(Mob body,LivingEntity attacker,Context context){
        var m=context.state().combat().maneuvers().get(context.order().owner());var rules=context.state().bootstrap().ruleset().melee();
        if(m==null || !m.open() || m.kind()!=MeleeManeuver.Kind.BLOCK || m.launchedAt()==0 || !m.matches(context.order())
                || !m.actor().body().equals(ActorBodyAuthority.current(context.state(),context.order().execution().actorId())) || context.tick()<m.launchedAt()
                || context.tick()>=m.launchedAt()+rules.maneuvers().travelTicks(m.kind()) || !context.order().target().entity().equals(attacker.getUUID())
                || !body.hasLineOfSight(attacker))return false;
        var item=context.state().inventory().items().get(context.order().weapon());
        if(item==null || !item.condition().usable() || !FrontierV3ExactItemPresentation.exactMatch(body.getMainHandItem(),item))return false;
        var facing=FrontierV3CombatPresentation.defenceFacing(body,m);
        return facing.isPresent() && MeleeAim.contains(facing.getAsDouble(),attacker.getX()-body.getX(),attacker.getZ()-body.getZ(),rules.training().number("defence.blockAngle"));
    }
    @SubscribeEvent(priority=EventPriority.LOW)
    public static void incoming(LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof Mob body) || !(event.getSource().getDirectEntity() instanceof LivingEntity attacker)
                || event.getSource().is(DamageTypeTags.BYPASSES_SHIELD) || event.getSource().getEntity()!=attacker)return;
        var c=context(body);if(c.isEmpty() || !blocking(body,attacker,c.orElseThrow()))return;
        float before=event.getAmount();int reduction=CombatMechanics.blockReduction(c.orElseThrow().order().profile());
        event.setAmount(before*(1000-reduction)/1000f);
        FrontierV3CombatTrace.defence((ServerLevel)body.level(),body,attacker,"BLOCK",before,event.getAmount());
    }
    @SubscribeEvent
    public static void knockback(LivingKnockBackEvent event){
        var c=context(event.getEntity());if(c.isEmpty())return;
        // Bounded permanent resilience, never immunity. Ordinary native knockback remains physical.
        int toughness=c.orElseThrow().order().profile().attributes().toughness();
        event.setStrength(event.getStrength()*CombatMechanics.interpolate(toughness,CombatTrainingCatalog.standard().number("attributes.resilienceMax"),CombatTrainingCatalog.standard().number("attributes.resilienceMin"))/1000f);
    }
    private FrontierV3CombatDefence(){}
}
