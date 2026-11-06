package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
/** Retained resource order. Selection owns no stock until the shared custody protocol confirms the take. */
public record RangedSupply(ActorExecutionId execution, CombatMethod method, SubjectId economicOwner,
        ActorContainerItemOrder order, Optional<ActorItemTransferStep> pending) {
    public RangedSupply {
        Objects.requireNonNull(execution);Objects.requireNonNull(economicOwner);Objects.requireNonNull(order);Objects.requireNonNull(pending);
        if(method!=CombatMethod.CROSSBOW || !execution.activityOwnerId().equals(order.ownerId()) || !execution.actorId().equals(order.actorId())
                || order.direction()!=ActorContainerItemOrder.Direction.TAKE || !(order.portion() instanceof ActorContainerItemOrder.Portion.Fungible p)
                || !p.destinationCustody().equals(new ResourceCustody.Actor(execution.actorId())) || p.claimId().isPresent()
                || pending.filter(step->!step.observation().actuation().execution().equals(execution)).isPresent())
            throw new IllegalArgumentException("invalid complete ammunition supply declaration");
    }
    public SubjectId owner(){return execution.activityOwnerId();}
    public RangedSupply prepared(ActorItemTransferStep step){if(pending.isPresent())throw new IllegalArgumentException("already prepared supply");return new RangedSupply(execution,method,economicOwner,order,Optional.of(step));}
    public RangedSupply resumed(ActorExecutionId successor,long revision){
        if(pending.isPresent() || !successor.actorId().equals(execution.actorId()) || !successor.activityOwnerId().equals(owner()) || successor.generation()<=execution.generation())
            throw new IllegalArgumentException("unsettled or foreign ammunition resumption");
        return new RangedSupply(successor,method,economicOwner,new ActorContainerItemOrder(order.ownerId(),order.actorId(),order.direction(),order.portion(),
                order.containerEndpoint(),order.station(),order.actorSlot(),order.goalOrdinal(),revision),Optional.empty());
    }
}
