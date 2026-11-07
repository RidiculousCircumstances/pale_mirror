package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.rest.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Native bed interactions through the common actor/block transaction protocol. No medical policy here. */
final class FrontierV3RestPlaceExecutor {
    private static final String MARKER="pm_v3_rest_operation";
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime){
        var s=runtime.decodedState().orElseThrow();long tick=runtime.executionView().orElseThrow().instant().ticks();
        if(level.getGameTime()%20==0)refreshAvailability(level,runtime,s);
        int visited=0;
        for(var use:s.humanPopulation().restPlaces().uses().values()){
            if(use.pendingBody().isPresent()){execute(level,runtime,use);continue;}
            if(ActorInventoryInteractionFences.pending(s,use.execution().actorId()))continue;
            var entity=level.getEntity(ActorBodyId.entityId(s.bootstrap().worldId(),use.execution().actorId()));if(!(entity instanceof Mob actor)||!actor.isAlive())continue;
            var policy=ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind());
            if(!s.actorExecutions().actors().get(use.execution().actorId()).current().equals(Optional.of(use.execution())))continue;
            var action=policy.restAction(s,use.execution(),use,tick);if(action.isEmpty()||s.actorMovements().containsKey(use.execution().actorId()))continue;
            if(action.orElseThrow()==RestUse.Action.RESTORE&&FrontierV3RestingPose.current(actor))continue;
            if(action.orElseThrow()!=RestUse.Action.EXIT&&!policy.permitsRest(s,use.execution(),use))continue;
            var head=position(use.place().head());var foot=position(use.place().foot());
            if(!FrontierV3LoadedWorld.ready(level,head)||!FrontierV3LoadedWorld.ready(level,foot))continue;
            if(action.orElseThrow()!=RestUse.Action.EXIT){
                var state=level.getBlockState(head);
                if(!state.isBed(level,head,actor))continue;
                if(FrontierV3RestingPose.otherSleeper(level,head,foot,actor)||action.orElseThrow()==RestUse.Action.ENTER
                        &&state.hasProperty(BedBlock.OCCUPIED)&&state.getValue(BedBlock.OCCUPIED)&&!actor.getSleepingPos().equals(Optional.of(head))){
                    FrontierV3CommandSubmission.propose(runtime,"rest-place-occupied",use.place().id().buildingId().value(),new RestEvent.Availability(use.place(),false));continue;
                }
                if(action.orElseThrow()==RestUse.Action.ENTER&&!FrontierV3SurfaceObservation.at(actor,use.approach()))continue;
            }
            if(!(action.orElseThrow()==RestUse.Action.EXIT&&actor.isSleeping())&&!FrontierV3ActorBodyController.inspectCurrent(level,runtime,actor))continue;s=runtime.decodedState().orElseThrow();
            FrontierV3ActorActuation permission;
            try{permission=FrontierV3ActorActuation.capture(s,actor,use.execution(),runtime::decodedState);}catch(IllegalArgumentException stale){continue;}
            if(!use.equals(s.humanPopulation().restPlaces().uses().get(use.execution().actorId())))continue;
            if(++visited>32)break;
            FrontierV3CommandSubmission.propose(runtime,"rest-place-prepare",use.execution().actorId().value(),new RestEvent.Prepared(use.execution().actorId(),use.revision(),permission.id(),action.orElseThrow()));
        }
    }
    private static void execute(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime,RestUse use){
        var actorId=use.execution().actorId();var actuation=use.pendingBody().orElseThrow();var action=use.pendingAction().orElseThrow();
        var before=new RestEvent.Prepared(actorId,use.revision(),actuation,action);var ledger=FrontierV3OperationLedger.get(level);
        var old=ledger.find(FrontierV3OperationReceipt.Protocol.REST_PLACE,actorId,FrontierV3OperationConfirmation.fingerprint(before));
        if(old.isPresent()&&ledger.ownerCommand(old.orElseThrow().ticket()).isPresent()){
            FrontierV3OperationConfirmation.submitRequired(level,runtime,old.orElseThrow().ticket(),ledger.ownerCommand(old.orElseThrow().ticket()).orElseThrow().consequence());return;
        }
        var s=runtime.decodedState().orElseThrow();var nativeActor=level.getEntity(ActorBodyId.entityId(s.bootstrap().worldId(),actorId));
        if(!(nativeActor instanceof Mob actor)){
            var unused=FrontierV3MinecraftOperation.cancelUnusedPreparation(level,FrontierV3OperationReceipt.Protocol.REST_PLACE,actorId,before);
            if(unused.isPresent()){
                var closed=new RestEvent.Changed(actorId,use.revision(),Optional.of(actuation),action,false,s.actorLocations().get(actorId).body());
                if(unused.orElseThrow().cancelledTicket().isPresent())FrontierV3OperationConfirmation.submitRequired(level,runtime,unused.orElseThrow().cancelledTicket().orElseThrow(),closed);
                else FrontierV3CommandSubmission.submit(runtime,"rest-place-unattempted",actorId.value(),closed);
            }return;
        }
        var head=position(use.place().head());var foot=position(use.place().foot());
        if(!FrontierV3LoadedWorld.ready(level,head)||!FrontierV3LoadedWorld.ready(level,foot))return;
        var participants=List.of(FrontierV3OperationEntityParticipant.current(actor,actuation.body().physicalEpoch()),FrontierV3OperationChunks.current(level,head),FrontierV3OperationChunks.current(level,foot));
        var operation=FrontierV3MinecraftOperation.applyDeferred(level,FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.REST_PLACE,actorId,before,participants),participants,
                ()->permitted(level,runtime,use,actor),new FrontierV3NativeEffect(()->FrontierV3NativeEffect.Admission.READY,()->{
                    if(!permitted(level,runtime,use,actor))return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
                    return changePose(level,actor,head,foot,action,actuation,use.revision());
                }),()->witness(actor,actuation,use.revision()));
        if(operation.pending())return;
        boolean applied=operation.complete();
        if(!applied&&operation.status()!=FrontierV3MinecraftOperation.Status.NOT_APPLIED&&operation.status()!=FrontierV3MinecraftOperation.Status.SOURCE_CHANGED)return;
        if(actor.isAlive()&&!FrontierV3ActorBodyController.inspectCurrent(level,runtime,actor))return;
        var position=runtime.decodedState().orElseThrow().actorLocations().get(actorId).body();
        FrontierV3OperationConfirmation.submitRequired(level,runtime,operation.ticket(),new RestEvent.Changed(actorId,use.revision(),Optional.of(actuation),action,applied,position));
    }
    static FrontierV3NativeEffect.Outcome changePose(ServerLevel level,Mob actor,BlockPos head,BlockPos foot,RestUse.Action action,ActorActuationId actuation,long revision){
        if(witness(actor,actuation,revision))return FrontierV3NativeEffect.Outcome.APPLIED;
        if(!actor.isAlive())return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
        if(action!=RestUse.Action.EXIT){
            if(!FrontierV3LoadedWorld.ready(level,head)||!FrontierV3LoadedWorld.ready(level,foot)||!level.getBlockState(head).isBed(level,head,actor)
                    ||FrontierV3RestingPose.otherSleeper(level,head,foot,actor)||actor.isSleeping()&&!actor.getSleepingPos().equals(Optional.of(head))
                    ||actor.distanceToSqr(head.getX()+.5,head.getY()+.6875,head.getZ()+.5)>(action==RestUse.Action.RESTORE?1:9))return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
            var bed=level.getBlockState(head);
            if(action==RestUse.Action.ENTER&&bed.hasProperty(BedBlock.OCCUPIED)&&bed.getValue(BedBlock.OCCUPIED)&&!actor.getSleepingPos().equals(Optional.of(head)))return FrontierV3NativeEffect.Outcome.REFUSED_WITHOUT_EFFECT;
        }
        actor.getNavigation().stop();if(action==RestUse.Action.EXIT){if(actor.isSleeping())actor.stopSleeping();}else actor.startSleeping(head);
        var marker=new CompoundTag();marker.putLong("revision",revision);marker.putString("owner",actuation.execution().activityOwnerId().value());marker.putLong("generation",actuation.execution().generation());
            marker.putLong("physicalEpoch",actuation.body().physicalEpoch());actor.getPersistentData().put(MARKER,marker);
        return FrontierV3NativeEffect.Outcome.APPLIED;
    }
    private static boolean witness(Mob actor,ActorActuationId actuation,long revision){
        var marker=actor.getPersistentData().getCompound(MARKER);
        return marker.getLong("revision")==revision&&marker.getString("owner").equals(actuation.execution().activityOwnerId().value())
                &&marker.getLong("generation")==actuation.execution().generation()&&marker.getLong("physicalEpoch")==actuation.body().physicalEpoch();
    }
    private static boolean permitted(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime,RestUse use,Mob actor){
        if(!actor.isAlive())return false;var action=use.pendingAction().orElseThrow();var s=runtime.decodedState().orElseThrow();
        try{if(!FrontierV3ActorActuation.capture(s,actor,use.execution(),runtime::decodedState).id().equals(use.pendingBody().orElseThrow()))return false;}
        catch(IllegalArgumentException stale){return false;}
        if(action==RestUse.Action.EXIT)return true;
        var head=position(use.place().head());var state=level.getBlockState(head);
        if(!state.isBed(level,head,actor)||FrontierV3RestingPose.otherSleeper(level,head,position(use.place().foot()),actor)
                ||action==RestUse.Action.ENTER&&state.hasProperty(BedBlock.OCCUPIED)&&state.getValue(BedBlock.OCCUPIED)&&!actor.getSleepingPos().equals(Optional.of(head)))return false;
        return action==RestUse.Action.ENTER?FrontierV3SurfaceObservation.at(actor,use.approach()):actor.distanceToSqr(head.getX()+0.5,head.getY()+0.6875,head.getZ()+0.5)<1;
    }
    private static void refreshAvailability(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime,FrontierWorldState s){
        if(s.humanPopulation().restPlaces().unavailable().isEmpty())return;
        for(var home:s.bootstrap().settlements())for(var bed:SettlementBeds.places(s,home))if(s.humanPopulation().restPlaces().unavailable().contains(bed.id())){
            var head=position(bed.head());if(!FrontierV3LoadedWorld.ready(level,head))continue;var state=level.getBlockState(head);
            if(state.getBlock() instanceof BedBlock&&(!state.hasProperty(BedBlock.OCCUPIED)||!state.getValue(BedBlock.OCCUPIED)))
                FrontierV3CommandSubmission.propose(runtime,"rest-place-free",bed.id().buildingId().value(),new RestEvent.Availability(bed.restPlace(),true));
        }
    }
    private static BlockPos position(BlockPosition p){return new BlockPos(p.x(),p.y(),p.z());}
    private FrontierV3RestPlaceExecutor(){}
}
