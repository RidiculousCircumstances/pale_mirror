package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.*;

/** A retained container-to-person portion. Policy owns its purpose; the resource ledger owns all stock. */
public record UnitResourceTransfer(SubjectId claimId, SubjectId actorId, SubjectId containerId,
        SubjectId sourceAccountId, SubjectId destinationAccountId, SubjectId sourceEconomicOwnerId,
        String itemKind, Map<SubjectId, Integer> lots, ActorItemSlot slot, SurfaceAnchor station,
        ActorExecutionId execution, Optional<ActorItemTransferStep> pending) {
    public UnitResourceTransfer {
        Objects.requireNonNull(claimId); Objects.requireNonNull(actorId); Objects.requireNonNull(containerId);
        Objects.requireNonNull(sourceAccountId); Objects.requireNonNull(destinationAccountId); Objects.requireNonNull(sourceEconomicOwnerId);
        Objects.requireNonNull(itemKind); lots = Map.copyOf(lots); Objects.requireNonNull(slot); Objects.requireNonNull(station);
        Objects.requireNonNull(execution); pending = Objects.requireNonNull(pending);
        if (!execution.actorId().equals(actorId) || slot instanceof ActorItemSlot.AttachedStorage)
            throw new IllegalArgumentException("personal replenishment needs its exact actor and personal destination");
        // The shared instruction validates bounded quantities and explicit custody independently of policy.
        portion(sourceAccountId, destinationAccountId, containerId, actorId, claimId, itemKind, lots);
        pending.ifPresent(step -> {
            if (!step.observation().actuation().execution().equals(execution))
                throw new IllegalArgumentException("resource replenishment changed execution across its prepared effect");
        });
    }
    public int quantity() { return lots.values().stream().mapToInt(Integer::intValue).sum(); }
    public ActorContainerItemOrder order(SubjectId ownerId, long revision) {
        return new ActorContainerItemOrder(ownerId, actorId, ActorContainerItemOrder.Direction.TAKE,
                portion(sourceAccountId, destinationAccountId, containerId, actorId, claimId, itemKind, lots),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(containerId), station, slot, 0, revision);
    }
    private static ActorContainerItemOrder.Portion.Fungible portion(SubjectId source, SubjectId destination, SubjectId container,
            SubjectId actor, SubjectId claim, String kind, Map<SubjectId, Integer> lots) {
        return new ActorContainerItemOrder.Portion.Fungible(source, new ResourceCustody.Container(container), destination,
                new ResourceCustody.Actor(actor), Optional.of(claim), kind, lots);
    }
    public UnitResourceTransfer prepare(ActorItemTransferStep step) {
        if (pending.isPresent()) throw new IllegalArgumentException("personal transfer already has its physical effect");
        return new UnitResourceTransfer(claimId, actorId, containerId, sourceAccountId, destinationAccountId,
                sourceEconomicOwnerId, itemKind, lots, slot, station, execution, Optional.of(step));
    }
}
