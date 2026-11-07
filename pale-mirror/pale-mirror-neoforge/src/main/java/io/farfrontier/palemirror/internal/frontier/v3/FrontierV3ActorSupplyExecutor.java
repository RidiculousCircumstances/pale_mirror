package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;

/** Source-neutral physical resupply. Registered activity ports own admission and exact consequences. */
final class FrontierV3ActorSupplyExecutor {
    interface Port {
        FrontierV3OperationReceipt.Protocol protocol();
        List<ActorSupplyVisit> visits(FrontierWorldState state);
        boolean available(FrontierWorldState state,ActorSupplyVisit visit);
        boolean service(FrontierWorldState state,ActorSupplyVisit visit);
        FrontierPayload prepared(ActorSupplyVisit visit,ActorItemTransferStep step);
        FrontierPayload loaded(ActorSupplyVisit visit,ActorItemTransferStep step,List<FungiblePhysicalObservation.Stack> source,List<FungiblePhysicalObservation.Stack> destination);
        FrontierPayload aborted(ActorSupplyVisit visit,ActorItemTransferStep step);
    }
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime,Port port){
        var state=runtime.decodedState().orElseThrow();int visited=0;
        for(var visit:port.visits(state)){
            var owner=visit.execution().activityOwnerId();var pending=visit.pending().orElse(null);
            var before=pending==null?null:port.prepared(visit,pending);
            var journal=FrontierV3OperationLedger.get(level);
            if(before!=null){
                var operation=journal.find(port.protocol(),owner,FrontierV3OperationConfirmation.fingerprint(before));
                if(operation.isPresent()){
                    var command=journal.ownerCommand(operation.orElseThrow().ticket());
                    if(command.isPresent()){FrontierV3OperationConfirmation.submitRequired(level,runtime,operation.orElseThrow().ticket(),command.orElseThrow().consequence());continue;}
                }
            }
            var entity=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),visit.execution().actorId()));
            if(!(entity instanceof Mob actor)){if(pending!=null)cancelUnused(level,runtime,port,visit,pending);continue;}
            if(++visited>32)break;
            if(pending==null){
                if(!actor.isAlive()||state.actorMovements().containsKey(visit.execution().actorId())||!FrontierV3SurfaceObservation.at(actor,visit.order().station())
                        ||!port.available(state,visit)||!port.service(state,visit))continue;
                FrontierV3ActorActuation permission;
                try{permission=FrontierV3ActorActuation.capture(state,actor,visit.execution(),runtime::decodedState);}catch(IllegalArgumentException stale){continue;}
                if(!FrontierV3ActorBodyController.inspectCurrent(level,runtime,actor)||!permission.current(actor))continue;
                state=runtime.decodedState().orElseThrow();if(!port.visits(state).contains(visit))continue;
                var lease=state.ambientLeases().get(visit.execution().actorId());if(lease==null||lease.status()!=AmbientLeaseStatus.HOT)continue;
                var preparation=MaterialSourcePreparation.review(state,visit.order());if(preparation.status()!=MaterialSourcePreparation.Status.READY)continue;
                var step=new ActorItemTransferStep(new ActorHotObservation(permission.id(),lease.revision()),preparation.requireReady(),permission.id().body().physicalEpoch());
                var session=FrontierV3ActorResourceTransfer.open(level,state,visit.order(),actor,step.source(),-1,0);
                if(session.filter(FrontierV3ActorResourceTransfer.Session::before).isPresent())FrontierV3CommandSubmission.propose(runtime,"actor-supply-prepare",owner.value(),port.prepared(visit,step));
                continue;
            }
            var session=FrontierV3ActorResourceTransfer.open(level,state,visit.order(),actor,pending.source(),pending.destinationSlot(),pending.destinationBefore()).orElse(null);
            if(session==null){cancelUnused(level,runtime,port,visit,pending);continue;}
            var participants=session.participants();
            var operation=FrontierV3MinecraftOperation.applyDeferred(level,FrontierV3MinecraftOperation.declaration(port.protocol(),owner,before,participants),participants,
                    ()->actor.isAlive()&&session.before(),FrontierV3NativeEffect.observed(session::apply),session::after);
            if(operation.pending())continue;
            if(operation.status()==FrontierV3MinecraftOperation.Status.NOT_APPLIED||operation.status()==FrontierV3MinecraftOperation.Status.SOURCE_CHANGED){
                FrontierV3OperationConfirmation.submitRequired(level,runtime,operation.ticket(),port.aborted(visit,pending));continue;
            }
            if(!operation.complete())continue;
            FrontierV3OperationConfirmation.submit(level,runtime,operation.ticket(),port.loaded(visit,pending,session.sourceLayout(),session.destinationLayout()),()->{
                session.ownerConfirmed();FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(),visit.execution().actorId(),actor);
            });
        }
    }
    private static void cancelUnused(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime,Port port,ActorSupplyVisit visit,ActorItemTransferStep step){
        var unused=FrontierV3MinecraftOperation.cancelUnusedPreparation(level,port.protocol(),visit.execution().activityOwnerId(),port.prepared(visit,step));
        if(unused.isEmpty())return;var payload=port.aborted(visit,step);
        if(unused.orElseThrow().cancelledTicket().isPresent())FrontierV3OperationConfirmation.submitRequired(level,runtime,unused.orElseThrow().cancelledTicket().orElseThrow(),payload);
        else FrontierV3CommandSubmission.submit(runtime,"actor-supply-unattempted",visit.execution().activityOwnerId().value(),payload);
    }
    private FrontierV3ActorSupplyExecutor(){}
}
