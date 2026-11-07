package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
/** Guard policy adapter for the common physical personal-supply executor. */
final class FrontierV3GuardAmmunitionExecutor {
    private static final FrontierV3ActorSupplyExecutor.Port PORT=new FrontierV3ActorSupplyExecutor.Port(){
        public FrontierV3OperationReceipt.Protocol protocol(){return FrontierV3OperationReceipt.Protocol.GUARD_AMMUNITION;}
        public List<ActorSupplyVisit> visits(FrontierWorldState s){return s.combat().ranged().supplies().values().stream().map(v->new ActorSupplyVisit(v.execution(),v.economicOwner(),v.order(),v.pending())).toList();}
        private GuardDuty duty(FrontierWorldState s,ActorSupplyVisit v){return GuardAuthority.require(s,v.execution());}
        public boolean available(FrontierWorldState s,ActorSupplyVisit v){return GuardAmmunition.sourceAvailable(s,duty(s,v));}
        public boolean service(FrontierWorldState s,ActorSupplyVisit v){return ServiceAccessCoordinator.available(s,GuardAuthority.service(duty(s,v)));}
        public FrontierPayload prepared(ActorSupplyVisit v,ActorItemTransferStep step){return new GuardAmmunitionEvent.Prepared(v.execution().activityOwnerId(),v.order().goalRevision(),step);}
        public FrontierPayload aborted(ActorSupplyVisit v,ActorItemTransferStep step){return new GuardAmmunitionEvent.Aborted(v.execution().activityOwnerId(),v.order().goalRevision(),step);}
        public FrontierPayload loaded(ActorSupplyVisit v,ActorItemTransferStep step,List<FungiblePhysicalObservation.Stack> source,List<FungiblePhysicalObservation.Stack> destination){return new
            GuardAmmunitionEvent.Loaded(v.execution().activityOwnerId(),v.order().goalRevision(),Optional.of(step),source,destination);}
    };
    static void tick(ServerLevel level,FrontierV3ServerRuntime<FrontierWorldState,?> runtime){FrontierV3ActorSupplyExecutor.tick(level,runtime,PORT);}
    private FrontierV3GuardAmmunitionExecutor(){}
}
