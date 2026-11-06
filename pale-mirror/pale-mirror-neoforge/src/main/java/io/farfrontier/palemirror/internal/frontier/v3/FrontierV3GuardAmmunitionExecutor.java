package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;
/** Guard adapter for the common container-to-person transaction. It never manufactures or projects ammunition. */
final class FrontierV3GuardAmmunitionExecutor {
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime){
        var state=runtime.decodedState().orElseThrow();int visited=0;
        for(var retained:state.combat().ranged().supplies().values()){
            var duty=state.strategicPlans().guards().get(retained.owner());if(duty==null)throw new IllegalStateException("ownerless ammunition supply");
            if(retained.pending().isPresent()){
                var prepared=new GuardAmmunitionEvent.Prepared(duty.id(),duty.revision(),retained.pending().orElseThrow());
                var journal=FrontierV3OperationLedger.get(level);
                var operation=journal.find(FrontierV3OperationReceipt.Protocol.GUARD_AMMUNITION,duty.id(),FrontierV3OperationConfirmation.fingerprint(prepared));
                if(operation.isPresent()){
                    var command=journal.ownerCommand(operation.orElseThrow().ticket());
                    if(command.isPresent()){FrontierV3OperationConfirmation.submitRequired(level,runtime,operation.orElseThrow().ticket(),command.orElseThrow().consequence());continue;}
                }
            }
            var actorEntity=level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(),retained.execution().actorId()));
            if(!(actorEntity instanceof Mob actor)){
                if(retained.pending().isPresent()) {
                    var preparation=new GuardAmmunitionEvent.Prepared(duty.id(),duty.revision(),retained.pending().orElseThrow());
                    var unused=FrontierV3MinecraftOperation.cancelUnusedPreparation(level,FrontierV3OperationReceipt.Protocol.GUARD_AMMUNITION,duty.id(),preparation);
                    if(unused.isPresent()) {
                        var aborted=new GuardAmmunitionEvent.Aborted(duty.id(),duty.revision(),retained.pending().orElseThrow());
                        if(unused.orElseThrow().cancelledTicket().isPresent())FrontierV3OperationConfirmation.submitRequired(level,runtime,unused.orElseThrow().cancelledTicket().orElseThrow(),aborted);
                        else FrontierV3CommandSubmission.submit(runtime,"guard-ammunition-unattempted",duty.id().value(),aborted);
                    }
                }
                continue;
            }
            if(++visited>32)break;
            var pending=retained.pending().orElse(null);
            if(pending==null && (!actor.isAlive() || state.actorMovements().containsKey(retained.execution().actorId())
                    || !FrontierV3SurfaceObservation.at(actor,retained.order().station()) || !GuardAmmunition.sourceAvailable(state,duty)))continue;
            var physical=FrontierV3PhysicalContainer.loaded(level,state,retained.order().containerEndpoint().containerId()).orElse(null);
            if(physical==null || !ReferenceContainerCustody.hasLiveCustody(state,physical.containerId()))continue;
            if(pending==null){
                if(!state.actorExecutions().actors().get(retained.execution().actorId()).current().equals(Optional.of(retained.execution()))
                        || !ReferenceContainerCustody.hasOperationalCustody(state,physical.containerId()) || !ServiceAccessCoordinator.available(state,GuardAuthority.service(duty)))continue;
                FrontierV3ActorActuation permission;
                try{permission=FrontierV3ActorActuation.capture(state,actor,retained.execution(),runtime::decodedState);}catch(IllegalArgumentException stale){continue;}
                if(!FrontierV3ActorBodyController.inspectCurrent(level,runtime,actor) || !permission.current(actor))continue;
                state=runtime.decodedState().orElseThrow();if(!retained.equals(state.combat().ranged().supplies().get(retained.owner())))continue;
                var lease=state.ambientLeases().get(retained.execution().actorId());if(lease==null || lease.status()!=AmbientLeaseStatus.HOT)continue;
                var preparation=MaterialSourcePreparation.review(state,retained.order());if(preparation.status()!=MaterialSourcePreparation.Status.READY)continue;
                var step=new ActorItemTransferStep(new ActorHotObservation(permission.id(),lease.revision()),preparation.requireReady(),permission.id().body().physicalEpoch());
                var transfer=new FrontierV3ActorItemTransfer.FungibleStep(retained.order(),physical,actor,actor.getUUID(),step.source(),-1,0);
                if(transfer.before())FrontierV3CommandSubmission.propose(runtime,"guard-ammunition-prepare",duty.id().value(),new GuardAmmunitionEvent.Prepared(duty.id(),duty.revision(),step));
                continue;
            }
            var before=new GuardAmmunitionEvent.Prepared(duty.id(),duty.revision(),pending);
            var ledger=FrontierV3OperationLedger.get(level);
            var old=ledger.find(FrontierV3OperationReceipt.Protocol.GUARD_AMMUNITION,duty.id(),FrontierV3OperationConfirmation.fingerprint(before));
            if(old.isPresent() && ledger.ownerCommand(old.orElseThrow().ticket()).isPresent()){
                FrontierV3OperationConfirmation.submitRequired(level,runtime,old.orElseThrow().ticket(),ledger.ownerCommand(old.orElseThrow().ticket()).orElseThrow().consequence());continue;
            }
            var transfer=new FrontierV3ActorItemTransfer.FungibleStep(retained.order(),physical,actor,actor.getUUID(),pending.source(),-1,0);
            var participants=List.of(physical.persistence().get(),FrontierV3OperationEntityParticipant.current(actor,pending.observation().actuation().body().physicalEpoch()));
            var operation=FrontierV3MinecraftOperation.applyDeferred(level,FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.GUARD_AMMUNITION,duty.id(),before,participants),
                    participants,()->actor.isAlive()&&transfer.before(),FrontierV3NativeEffect.observed(transfer::apply),transfer::after);
            if(operation.pending())continue;
            if(operation.status()==FrontierV3MinecraftOperation.Status.NOT_APPLIED || operation.status()==FrontierV3MinecraftOperation.Status.SOURCE_CHANGED){
                FrontierV3OperationConfirmation.submitRequired(level,runtime,operation.ticket(),new GuardAmmunitionEvent.Aborted(duty.id(),duty.revision(),pending));continue;
            }
            if(!operation.complete())continue;
            var remaining=FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(physical.inventory(),state,physical.containerId());
            var destination=List.of(new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(retained.execution().actorId(),actor,retained.order().actorSlot()),retained.order().portion().itemKind(),
                    retained.order().portion().quantity()));
            FrontierV3OperationConfirmation.submit(level,runtime,operation.ticket(),new GuardAmmunitionEvent.Loaded(duty.id(),duty.revision(),Optional.of(pending),remaining,destination),
                    ()->FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(),retained.execution().actorId(),actor));
        }
    }
    private FrontierV3GuardAmmunitionExecutor(){}
}
