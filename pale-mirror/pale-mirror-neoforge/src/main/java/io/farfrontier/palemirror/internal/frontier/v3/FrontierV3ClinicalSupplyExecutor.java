package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
/** Medicine policy adapter; shared storage/ground endpoints execute the exact same pickup protocol. */
final class FrontierV3ClinicalSupplyExecutor {
    private static final FrontierV3ActorSupplyExecutor.Port PORT=new FrontierV3ActorSupplyExecutor.Port(){
        public FrontierV3OperationReceipt.Protocol protocol(){return FrontierV3OperationReceipt.Protocol.CLINICAL_SUPPLY;}
        public List<ActorSupplyVisit> visits(FrontierWorldState s){return s.humanPopulation().clinical().tasks().values().stream().flatMap(t->t.supply().stream()).toList();}
        public boolean available(FrontierWorldState s,ActorSupplyVisit v){return ActorSupplyVisits.available(s,v);}
        public boolean service(FrontierWorldState s,ActorSupplyVisit v){return ClinicalTransitions.serviceAvailable(s,v);}
        public FrontierPayload prepared(ActorSupplyVisit v,ActorItemTransferStep step){return new ClinicalEvent.SupplyPrepared(v.execution().activityOwnerId(),v.order().goalRevision(),step);}
        public FrontierPayload aborted(ActorSupplyVisit v,ActorItemTransferStep step){return new ClinicalEvent.SupplyAborted(v.execution().activityOwnerId(),v.order().goalRevision(),Optional.of(step));}
        public FrontierPayload loaded(ActorSupplyVisit v,ActorItemTransferStep step,List<FungiblePhysicalObservation.Stack> source,List<FungiblePhysicalObservation.Stack> destination){return new
            ClinicalEvent.Supplied(v.execution().activityOwnerId(),v.order().goalRevision(),Optional.of(step),source,destination);}
    };
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime){FrontierV3ActorSupplyExecutor.tick(level,runtime,PORT);}
    private FrontierV3ClinicalSupplyExecutor(){}
}
