package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates the actor-order declaration and its observed physical addresses. */
final class FungibleActorOrderTransfer {
    private FungibleActorOrderTransfer() { }

    record Accounts(CustodyAccount source, CustodyAccount destination, boolean destinationExists,
                    Map<SubjectId, Integer> lots, Map<SubjectId, Integer> claims) { }

    static Accounts accounts(FungibleResourceLedger ledger, ActorContainerItemOrder order) {
        Objects.requireNonNull(order, "actor item order");
        if (!(order.portion() instanceof ActorContainerItemOrder.Portion.Fungible portion))
            throw new IllegalArgumentException("resource handoff requires a fungible order");
        CustodyAccount source = ledger.accounts().get(portion.sourceAccountId());
        if (source == null) throw new IllegalArgumentException("unknown actor-order source account");
        CustodyAccount existing = ledger.accounts().get(portion.destinationAccountId());
        if (!source.custody().equals(portion.sourceCustody())
                || existing != null && !existing.custody().equals(portion.destinationCustody()))
            throw new IllegalArgumentException("actor item order has stale source or destination custody");
        if (order.direction() == ActorContainerItemOrder.Direction.TAKE && existing == null)
            ActorCarriedResources.requireNewAccountCapacity(ledger, order.actorId(), portion.destinationAccountId());
        for (var entry : portion.lotQuantities().entrySet()) {
            ResourceLot lot = ledger.lots().get(entry.getKey());
            if (lot == null || !lot.itemKind().equals(portion.itemKind()))
                throw new IllegalArgumentException("actor item order mixes physical item kinds");
        }
        Map<SubjectId, Integer> claimed;
        if (portion.claimId().isPresent()) {
            SubjectId claimId = portion.claimId().orElseThrow();
            ClaimAllocation claim = ledger.claims().get(claimId);
            if (claim == null || !claim.claimantId().equals(order.ownerId())
                    || !claim.itemKind().equals(portion.itemKind())
                    || claim.quantity() != portion.quantity()
                    || !claim.lotQuantities().equals(portion.lotQuantities()))
                throw new IllegalArgumentException("actor item order has no exact owner-held claim");
            claimed = Map.of(claimId, portion.quantity());
        } else {
            if (!source.claimQuantities().isEmpty())
                throw new IllegalArgumentException("unclaimed actor transfer cannot consume reserved stock");
            claimed = Map.of();
        }
        FungibleResourceLedger.requireSubset(source.lotQuantities(), portion.lotQuantities(), "actor order source lots");
        FungibleResourceLedger.requireOptionalSubset(source.claimQuantities(), claimed, "actor order source claim");
        CustodyAccount destination = existing == null
                ? new CustodyAccount(portion.destinationAccountId(), portion.destinationCustody(), portion.lotQuantities(), claimed)
                : existing;
        return new Accounts(source, destination, existing != null, portion.lotQuantities(), claimed);
    }

    static FungibleResourceLedger observedStacks(FungibleResourceLedger ledger, ActorContainerItemOrder order,
                                                 long sourceEpoch, long destinationEpoch,
                                                 List<FungiblePhysicalObservation.Stack> remainingSource,
                                                 List<FungiblePhysicalObservation.Stack> destination) {
        Accounts accounts = accounts(ledger, order);
        validateAddresses(order, accounts.source().custody(), remainingSource);
        validateAddresses(order, accounts.destination().custody(), destination);
        FungibleResourceLedger unbound = new FungibleResourceLedger(ledger.lots(), ledger.claims(), ledger.accounts(), Map.of());
        FungibleResourceLedger expected = unbound.transferActorOrderCold(order);
        List<PhysicalStackBinding> sourceBindings = expected.accounts().containsKey(accounts.source().id())
                ? FungiblePhysicalObservation.bind(expected, accounts.source().id(), sourceEpoch, remainingSource)
                : List.of();
        if (!expected.accounts().containsKey(accounts.source().id()) && !remainingSource.isEmpty())
            throw new IllegalArgumentException("observed actor handoff retains an unowned source stack");
        List<PhysicalStackBinding> destinationBindings = FungiblePhysicalObservation.bind(expected,
                accounts.destination().id(), destinationEpoch, destination);
        return ledger.transferActorOrderObserved(order, sourceEpoch, destinationEpoch, sourceBindings, destinationBindings);
    }

    static void requireDeclaredStationPort(FungibleResourceLedger ledger, ActorContainerItemOrder order,
                                           Accounts transfer, List<PhysicalStackBinding> destinationBindings) {
        if (!(order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.FungibleStation station)) return;
        InventoryCustody.ContainerSlot expectedSlot = new InventoryCustody.ContainerSlot(station.containerId(),
                station.port() == ActorContainerItemOrder.StationPort.INPUT
                        ? station.spec().inputSlot() : station.spec().outputSlot());
        List<PhysicalStackBinding> stationBindings = order.direction() == ActorContainerItemOrder.Direction.PLACE
                ? destinationBindings
                : ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(transfer.source().id())).toList();
        boolean exactPort = stationBindings.stream().anyMatch(binding ->
                binding.address().equals(new PhysicalStackAddress.ContainerSlot(expectedSlot))
                        && transfer.lots().entrySet().stream().allMatch(entry ->
                        binding.lotQuantities().getOrDefault(entry.getKey(), 0) >= entry.getValue()));
        if (!exactPort) throw new IllegalArgumentException("observed actor transfer did not use its declared machine port");
    }

    static void validateBindingAddresses(ActorContainerItemOrder order, ResourceCustody custody,
                                         List<PhysicalStackBinding> bindings) {
        validateAddresses(order, custody, bindings.stream().map(binding -> new FungiblePhysicalObservation.Stack(
                binding.address(), binding.itemKind(), binding.quantity())).toList());
    }

    private static void validateAddresses(ActorContainerItemOrder order, ResourceCustody custody,
                                          List<FungiblePhysicalObservation.Stack> stacks) {
        for (FungiblePhysicalObservation.Stack stack : stacks) {
            boolean correct = switch (custody) {
                case ResourceCustody.Container container -> stack.address() instanceof PhysicalStackAddress.ContainerSlot slot
                        && slot.slot().containerId().equals(container.containerId())
                        && (order.containerEndpoint() instanceof ActorContainerItemOrder.ContainerEndpoint.FungibleStation station
                        ? order.direction() == ActorContainerItemOrder.Direction.PLACE
                        && slot.slot().slot() == (station.port() == ActorContainerItemOrder.StationPort.INPUT
                        ? station.spec().inputSlot() : station.spec().outputSlot())
                        : true);
                case ResourceCustody.Actor actor -> actor.actorId().equals(order.actorId())
                        && switch (order.actorSlot()) {
                            case ActorItemSlot.Hand declared -> stack.address() instanceof PhysicalStackAddress.ActorHand hand
                                    && hand.actorId().equals(actor.actorId()) && hand.hand() == declared.hand();
                            case ActorItemSlot.Pocket pocket -> stack.address() instanceof PhysicalStackAddress.ActorPocket address
                                    && address.actorId().equals(actor.actorId()) && address.slot() == pocket.index();
                        };
                default -> false;
            };
            if (!correct) throw new IllegalArgumentException("observed actor handoff has a foreign physical address");
        }
    }
}
