package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Native HP is changed only under a durable exact preimage, then observed by the common body owner. */
final class FrontierV3ClinicalHealingExecutor {
    private static final String MARKER="pm_v3_clinical_heal";
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime){
        var s=runtime.decodedState().orElseThrow();int visited=0;long tick=runtime.executionView().orElseThrow().instant().ticks();
        for(var pending:s.humanPopulation().clinical().healing().values())execute(level,runtime,pending);
        for(var wound:s.humanPopulation().clinical().wounds().values()){
            var actor=wound.actor();if(ActorInventoryInteractionFences.pending(s,actor)||s.humanPopulation().clinical().healing().containsKey(actor)||tick<wound.evaluatedAt()+s.bootstrap().ruleset().clinical().reviewTicks()
                    ||!ClinicalAuthority.alive(s,actor)||s.humanPopulation().clinical().tasks().values().stream().anyMatch(t->t.patient().equals(actor)&&t.tend().isPresent()))continue;
            var entity=level.getEntity(ActorBodyId.entityId(s.bootstrap().worldId(),actor));if(!(entity instanceof Mob body)||!body.isAlive())continue;
            if(++visited>32)break;
            var execution=s.actorExecutions().actors().get(actor);if(execution==null||execution.current().isEmpty())continue;
            if(!FrontierV3ActorBodyController.inspectCurrent(level,runtime,body))continue;s=runtime.decodedState().orElseThrow();
            if(wound.observedHealth()!=s.actorLocations().get(actor).condition().health().raw())continue;
            var amount=ClinicalAuthority.recovery(s,wound,tick);
            if(amount==0){FrontierV3CommandSubmission.propose(runtime,"clinical-empty-recovery",actor.value(),new ClinicalEvent.Recovered(actor,Optional.empty(),tick,0,true));continue;}
            FrontierV3ActorActuation permission;
            try{permission=FrontierV3ActorActuation.capture(s,body,execution.current().orElseThrow(),runtime::decodedState);}catch(IllegalArgumentException stale){continue;}
            var healing=new ClinicalHealing(permission.id(),wound.observedHealth(),amount,tick,wound.harmRevision(),tick+1);
            FrontierV3CommandSubmission.propose(runtime,"clinical-healing-prepare",actor.value(),new ClinicalEvent.HealingPrepared(healing));
        }
    }
    private static void execute(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime,ClinicalHealing h){
        var actor=h.actor().execution().actorId();var before=new ClinicalEvent.HealingPrepared(h);var ledger=FrontierV3OperationLedger.get(level);
        var old=ledger.find(FrontierV3OperationReceipt.Protocol.CLINICAL_HEAL,actor,FrontierV3OperationConfirmation.fingerprint(before));
        if(old.isPresent()&&ledger.ownerCommand(old.orElseThrow().ticket()).isPresent()){
            FrontierV3OperationConfirmation.submitRequired(level,runtime,old.orElseThrow().ticket(),ledger.ownerCommand(old.orElseThrow().ticket()).orElseThrow().consequence());return;
        }
        var s=runtime.decodedState().orElseThrow();var entity=level.getEntity(ActorBodyId.entityId(s.bootstrap().worldId(),actor));
        if(!(entity instanceof Mob body)){
            var unused=FrontierV3MinecraftOperation.cancelUnusedPreparation(level,FrontierV3OperationReceipt.Protocol.CLINICAL_HEAL,actor,before);
            if(unused.isPresent()){
                var e=closed(h,false);
                if(unused.orElseThrow().cancelledTicket().isPresent())FrontierV3OperationConfirmation.submitRequired(level,runtime,unused.orElseThrow().cancelledTicket().orElseThrow(),e);
                else FrontierV3CommandSubmission.submit(runtime,"clinical-healing-unattempted",actor.value(),e);
            }return;
        }
        var participants=List.of(FrontierV3OperationEntityParticipant.current(body,h.actor().body().physicalEpoch()));
        var operation=FrontierV3MinecraftOperation.applyDeferred(level,FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.CLINICAL_HEAL,actor,before,participants),participants,
                ()->permitted(runtime,body,h),new FrontierV3NativeEffect(()->FrontierV3NativeEffect.Admission.READY,()->{
                    if(!permitted(runtime,body,h))return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
                    return heal(body,h);
                }),()->body.getPersistentData().getCompound(MARKER).getLong("attempt")==h.attempt());
        if(operation.pending())return;
        if(operation.complete()){
            if(body.isAlive()&&!FrontierV3ActorBodyController.inspectCurrent(level,runtime,body))return;
            FrontierV3OperationConfirmation.submitRequired(level,runtime,operation.ticket(),closed(h,true));
        }else if(operation.status()==FrontierV3MinecraftOperation.Status.NOT_APPLIED||operation.status()==FrontierV3MinecraftOperation.Status.SOURCE_CHANGED)
            FrontierV3OperationConfirmation.submitRequired(level,runtime,operation.ticket(),closed(h,false));
    }
    static FrontierV3NativeEffect.Outcome heal(Mob body,ClinicalHealing healing){
        if(body.getPersistentData().getCompound(MARKER).getLong("attempt")==healing.attempt())return FrontierV3NativeEffect.Outcome.APPLIED;
        if(!body.isAlive()||FrontierV3HealthObservation.survivor(body.getHealth()).filter(h->h.raw()==healing.before()).isEmpty())return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
        body.heal((float)((double)healing.amount()/FixedScalar.SCALE));
        var marker=new CompoundTag();marker.putLong("attempt",healing.attempt());body.getPersistentData().put(MARKER,marker);return FrontierV3NativeEffect.Outcome.APPLIED;
    }
    private static ClinicalEvent.Recovered closed(ClinicalHealing h,boolean applied){return new ClinicalEvent.Recovered(h.actor().execution().actorId(),Optional.of(h),h.throughTick(),h.amount(),applied);}
    private static boolean permitted(FrontierV3ServerRuntime<FrontierWorldState,?> runtime,Mob body,ClinicalHealing h){
        if(!body.isAlive()||FrontierV3HealthObservation.survivor(body.getHealth()).filter(value->value.raw()==h.before()).isEmpty())return false;
        try{return FrontierV3ActorActuation.capture(runtime.decodedState().orElseThrow(),body,h.actor().execution(),runtime::decodedState).id().equals(h.actor());}
        catch(IllegalArgumentException stale){return false;}
    }
    private FrontierV3ClinicalHealingExecutor(){}
}
