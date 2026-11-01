package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
/** Read-only actual stock/capacity request. No resident, execution, route or synthetic stock. */
record InternalHaulRequest(ResourceSourceEndpoint source,ShipmentEndpoint receiver,String kind,int quantity) {
    InternalHaulRequest {
        Objects.requireNonNull(source);Objects.requireNonNull(receiver);Objects.requireNonNull(kind);
        if(quantity<1 || quantity>64)throw new IllegalArgumentException("invalid pedestrian haul portion");
    }
    static Optional<InternalHaulRequest> select(FrontierWorldState state,SubjectId home,ResourceSourceEndpoint source,
            ShipmentEndpoint receiver,String kind,int maximum) {
        if(maximum<1 || source.container().filter(receiver.containerId()::equals).isPresent()
                || source.container().filter(id->ReferenceContainerCustody.blocksCanonicalUse(state,id)).isPresent()
                || ReferenceContainerCustody.blocksCanonicalUse(state,receiver.containerId()))return Optional.empty();
        var ledger=state.inventory().fungibleResources();
        var account=FungibleResourceCustodySupport.accountsAt(ledger,source.custody()).stream().findFirst().orElse(null);
        if(account==null || !InternalShipmentStateSupport.flowAvailable(state,account.id(),receiver.containerId(),kind))return Optional.empty();
        int count=Math.min(Math.min(64,maximum),ledger.unclaimedQuantity(account.id(),home,kind));
        while(count>0 && !ContainerStorageAdmission.receive(state,(ResourceCustody.Container)receiver.custody(),kind,count,Optional.empty()))count--;
        return count==0?Optional.empty():Optional.of(new InternalHaulRequest(source,receiver,kind,count));
    }
    Optional<InternalShipmentDispatched> prepare(FrontierWorldState state,ResidentProfile worker,long tick) {
        return InternalShipmentPlanning.prepare(state,worker,source,receiver,kind,quantity,tick);
    }
}
