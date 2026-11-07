package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Native dressing is a finite procedure over one actual carried bandage, without instant healing. */
final class FrontierV3ClinicalTreatmentExecutor {
    private static final String MARKER="pm_v3_clinical_tend";
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime){
        var state=runtime.decodedState().orElseThrow();int visited=0;
        for(var task:state.humanPopulation().clinical().tasks().values()){
            if(task.tend().isPresent()){execute(level,runtime,task,task.tend().orElseThrow());continue;}
            if(task.phase()!=ClinicalTask.Phase.TEND||task.pending()||!ClinicalAuthority.current(state,task)
                    ||ActorInventoryInteractionFences.pending(state,task.actor())||ActorInventoryInteractionFences.pending(state,task.patient()))continue;
            var performer=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),task.actor()));
            var target=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),task.patient()));
            if(!(performer instanceof Mob medic)||!(target instanceof Mob patient)||!medic.isAlive()||!patient.isAlive()||medic.distanceTo(patient)>3||medic!=patient&&!medic.hasLineOfSight(patient))continue;
            var wound=state.humanPopulation().clinical().wounds().get(task.patient());
            if(wound==null||!wound.needsDressing(ClinicalAuthority.quality(state,task))||state.humanPopulation().clinical().healing().containsKey(task.patient())
                    ||state.humanPopulation().clinical().healing().containsKey(task.actor()))continue;
            var bandage=ActorConsumables.spendable(state,task.actor(),task.home(),state.bootstrap().ruleset().clinical().bandage());if(bandage.isEmpty())continue;
            var patientExecution=state.actorExecutions().actors().get(task.patient()).current();if(patientExecution.isEmpty())continue;
            FrontierV3ActorActuation permission,patientPermission;
            try{permission=FrontierV3ActorActuation.capture(state,medic,task.execution(),runtime::decodedState);patientPermission=FrontierV3ActorActuation.capture(state,patient,patientExecution.orElseThrow(),runtime::decodedState);}
            catch(IllegalArgumentException stale){continue;}
            if(!FrontierV3ActorBodyController.inspectCurrent(level,runtime,medic)||!FrontierV3ActorBodyController.inspectCurrent(level,runtime,patient)||!permission.current(medic)||!patientPermission.current(patient))continue;
            state=runtime.decodedState().orElseThrow();if(!task.equals(state.humanPopulation().clinical().tasks().get(task.id()))
                    ||ActorInventoryInteractionFences.pending(state,task.actor())||ActorInventoryInteractionFences.pending(state,task.patient())
                    ||wound.observedHealth()!=state.actorLocations().get(task.patient()).condition().health().raw())continue;
            long tick=runtime.executionView().orElseThrow().instant().ticks();var rules=state.bootstrap().ruleset().clinical();var binding=bandage.orElseThrow();
            var tend=new ClinicalTend(permission.id(),patientPermission.id(),binding,binding.lotQuantities().keySet().stream().min(Comparator.naturalOrder()).orElseThrow(),wound,
                    ClinicalAuthority.quality(state,task),rules.coverageUnits(),tick+1,tick,rules.procedureTicks(state.humanPopulation().resident(task.actor()).skill(ResidentSkill.MEDICINE)));
            if(++visited>32)break;
            FrontierV3CommandSubmission.propose(runtime,"clinical-tend-prepare",task.id().value(),new ClinicalEvent.TendPrepared(task.id(),task.revision(),tend));
        }
    }
    private static void execute(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime,ClinicalTask task,ClinicalTend tend){
        var before=new ClinicalEvent.TendPrepared(task.id(),task.revision(),tend);var ledger=FrontierV3OperationLedger.get(level);
        var prior=ledger.find(FrontierV3OperationReceipt.Protocol.CLINICAL_TEND,task.id(),FrontierV3OperationConfirmation.fingerprint(before));
        if(prior.isPresent()&&ledger.ownerCommand(prior.orElseThrow().ticket()).isPresent()){
            FrontierV3OperationConfirmation.submitRequired(level,runtime,prior.orElseThrow().ticket(),ledger.ownerCommand(prior.orElseThrow().ticket()).orElseThrow().consequence());return;
        }
        var state=runtime.decodedState().orElseThrow();var entity=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),task.actor()));
        if(!(entity instanceof Mob medic)){
            var unused=FrontierV3MinecraftOperation.cancelUnusedPreparation(level,FrontierV3OperationReceipt.Protocol.CLINICAL_TEND,task.id(),before);
            if(unused.isPresent()){
                var e=closed(task,tend,false);
                if(unused.orElseThrow().cancelledTicket().isPresent())FrontierV3OperationConfirmation.submitRequired(level,runtime,unused.orElseThrow().cancelledTicket().orElseThrow(),e);
                else FrontierV3CommandSubmission.submit(runtime,"clinical-tend-unattempted",task.id().value(),e);
            }return;
        }
        var target=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),task.patient()));var patient=target instanceof Mob mob?mob:null;
        var participants=List.of(FrontierV3OperationEntityParticipant.current(medic,tend.performer().body().physicalEpoch()));
        var operation=FrontierV3MinecraftOperation.applyDeferred(level,FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.CLINICAL_TEND,task.id(),before,participants),participants,
                ()->permitted(runtime,task,tend,medic,patient)&&bandageBefore(medic,tend),new FrontierV3NativeEffect(()->{
                    if(runtime.executionView().orElseThrow().instant().ticks()<tend.startedAt()+tend.duration()){
                        if(patient!=null)medic.getLookControl().setLookAt(patient,30,30);
                        if(level.getGameTime()%20==0)medic.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        return FrontierV3NativeEffect.Admission.waiting("clinical-procedure");
                    }return FrontierV3NativeEffect.Admission.READY;
                },()->{
                    if(!permitted(runtime,task,tend,medic,patient)||!bandageBefore(medic,tend))return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
                    return dress(medic,patient,tend);
                }),()->applied(medic,task,tend));
        if(operation.pending())return;
        if(operation.complete())FrontierV3OperationConfirmation.submit(level,runtime,operation.ticket(),closed(task,tend,true),()->FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(),task.actor(),medic));
        else if(operation.status()==FrontierV3MinecraftOperation.Status.NOT_APPLIED||operation.status()==FrontierV3MinecraftOperation.Status.SOURCE_CHANGED)
            FrontierV3OperationConfirmation.submitRequired(level,runtime,operation.ticket(),closed(task,tend,false));
    }
    static FrontierV3NativeEffect.Outcome dress(Mob medic,Mob patient,ClinicalTend tend){
        var marker=medic.getPersistentData().getCompound(MARKER);var owner=tend.performer().execution().activityOwnerId();
        if(marker.getString("owner").equals(owner.value())&&marker.getLong("attempt")==tend.attempt())return FrontierV3NativeEffect.Outcome.APPLIED;
        if(patient==null||!medic.isAlive()||!patient.isAlive()||medic.distanceTo(patient)>3||medic!=patient&&!medic.hasLineOfSight(patient)||!bandageBefore(medic,tend)
                ||FrontierV3HealthObservation.survivor(patient.getHealth()).filter(h->h.raw()==tend.wound().observedHealth()).isEmpty())return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
        var slot=FrontierV3ActorResourceSlots.slot(tend.bandage().address());var actual=FrontierV3ActorResourceSlots.get(medic,slot);
        FrontierV3ActorResourceSlots.set(medic,slot,actual.getCount()==1?ItemStack.EMPTY:actual.copyWithCount(actual.getCount()-1));
        marker=new CompoundTag();marker.putString("owner",owner.value());marker.putLong("attempt",tend.attempt());medic.getPersistentData().put(MARKER,marker);
        medic.swing(net.minecraft.world.InteractionHand.MAIN_HAND);return FrontierV3NativeEffect.Outcome.APPLIED;
    }
    private static ClinicalEvent.Tended closed(ClinicalTask task,ClinicalTend tend,boolean applied){
        var remaining=!applied||tend.bandage().quantity()==1?List.<FungiblePhysicalObservation.Stack>of():List.of(new FungiblePhysicalObservation.Stack(tend.bandage().address(),tend.bandage().itemKind(),tend.bandage().quantity()-1));
        return new ClinicalEvent.Tended(task.id(),task.revision(),Optional.of(tend),Optional.empty(),Optional.empty(),applied,remaining);
    }
    private static boolean permitted(FrontierV3ServerRuntime<FrontierWorldState,?> runtime,ClinicalTask task,ClinicalTend tend,Mob medic,Mob patient){
        if(patient==null||!medic.isAlive()||!patient.isAlive()||medic.distanceTo(patient)>3||medic!=patient&&!medic.hasLineOfSight(patient))return false;
        var s=runtime.decodedState().orElseThrow();
        try{if(!FrontierV3ActorActuation.capture(s,medic,tend.performer().execution(),runtime::decodedState).id().equals(tend.performer())
                ||!FrontierV3ActorActuation.capture(s,patient,tend.patient().execution(),runtime::decodedState).id().equals(tend.patient()))return false;}
        catch(IllegalArgumentException stale){return false;}
        return task.equals(s.humanPopulation().clinical().tasks().get(task.id()))&&!s.actorMovements().containsKey(task.actor())&&!s.actorMovements().containsKey(task.patient())
                &&FrontierV3HealthObservation.survivor(patient.getHealth()).filter(h->h.raw()==tend.wound().observedHealth()).isPresent();
    }
    private static boolean bandageBefore(Mob medic,ClinicalTend tend){var actual=FrontierV3ActorResourceSlots.get(medic,FrontierV3ActorResourceSlots.slot(tend.bandage().address()));
        return actual.getCount()==tend.bandage().quantity()&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(tend.bandage().itemKind())
                &&ItemStack.isSameItemSameComponents(actual,new ItemStack(actual.getItem(),actual.getCount()));}
    private static boolean applied(Mob medic,ClinicalTask task,ClinicalTend tend){var marker=medic.getPersistentData().getCompound(MARKER);return marker.getString("owner").equals(task.id().value())&&marker.getLong("attempt")==tend.attempt();}
    private FrontierV3ClinicalTreatmentExecutor(){}
}
