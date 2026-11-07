package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.*;

/** Common carried-supply visit. The activity owns its phase; ordinary transfer owns custody. */
public record ActorSupplyVisit(ActorExecutionId execution,SubjectId economicOwner,ActorResourceItemOrder order,Optional<ActorItemTransferStep> pending){
    public ActorSupplyVisit {
        Objects.requireNonNull(execution);Objects.requireNonNull(economicOwner);Objects.requireNonNull(order);Objects.requireNonNull(pending);
        if(!execution.actorId().equals(order.actorId())||!execution.activityOwnerId().equals(order.ownerId())
                ||order.direction()!=ActorContainerItemOrder.Direction.TAKE||!(order.portion() instanceof ActorContainerItemOrder.Portion.Fungible))
            throw new IllegalArgumentException("foreign carried-supply visit");
        var p=(ActorContainerItemOrder.Portion.Fungible)order.portion();
        if(!p.destinationCustody().equals(new ResourceCustody.Actor(execution.actorId()))||p.claimId().isPresent()||p.delegation().isPresent()
                ||pending.filter(v->!v.observation().actuation().execution().equals(execution)).isPresent()
                ||order instanceof ActorContainerItemOrder c&&!(c.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.FungibleContainer))
            throw new IllegalArgumentException("personal supply requires exact unclaimed actor stock and a declared warehouse/ground source");
    }
    public ActorSupplyVisit prepared(ActorItemTransferStep step){
        if(pending.isPresent())throw new IllegalArgumentException("supply visit already preparing");
        return new ActorSupplyVisit(execution,economicOwner,order,Optional.of(step));
    }
}
