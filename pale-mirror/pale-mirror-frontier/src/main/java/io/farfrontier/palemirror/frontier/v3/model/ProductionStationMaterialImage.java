package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** One complete input-compartment preimage and its exact claimed decrements. No slot is a material identity. */
record ProductionStationMaterialImage(long epoch, List<FungiblePhysicalObservation.Stack> before,
                                      List<FungiblePhysicalObservation.Stack> remaining) {
    static ProductionStationMaterialImage of(FungibleResourceLedger ledger, ProductionStationSpec station,
            ProductionOperation.FungibleInput input) {
        var account = Objects.requireNonNull(ledger.accounts().get(input.accountId()), "station input account");
        if (!account.custody().equals(station.custody(ActorContainerItemOrder.StationPort.INPUT)))
            throw new IllegalArgumentException("operation material image has a foreign compartment");
        var decrements = new HashMap<PhysicalStackAddress, Integer>();
        var covered = new HashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Integer>();
        for (var entry : input.claims().entrySet()) {
            var claim = Objects.requireNonNull(ledger.claims().get(entry.getKey()), "station input claim");
            if (claim.quantity() != entry.getValue() || claim.lotQuantities().isEmpty())
                throw new IllegalArgumentException("material image requires complete pinned claims");
            claim.lotQuantities().forEach((id, quantity) -> covered.merge(id, quantity, Math::addExact));
            for (var slice : MaterialSourceSelection.select(ledger, input.accountId(), claim.itemKind(), claim.quantity(), Optional.of(claim.id())))
                decrements.merge(slice.address(), slice.moved(), Math::addExact);
        }
        if (!covered.equals(input.lots())) throw new IllegalArgumentException("station input claims differ from its exact material allocation");
        var before = new ArrayList<FungiblePhysicalObservation.Stack>();
        var after = new ArrayList<FungiblePhysicalObservation.Stack>(); long epoch = 0;
        for (var binding : ledger.bindings().values().stream().filter(value -> value.accountId().equals(input.accountId()))
                .sorted(Comparator.comparing(PhysicalStackBinding::id)).toList()) {
            if (epoch == 0) epoch = binding.authorityEpoch();
            if (binding.authorityEpoch() != epoch || !(binding.address() instanceof PhysicalStackAddress.ContainerSlot slot)
                    || !slot.slot().containerId().equals(station.containerId()) || !station.inputSlots().contains(slot.slot().slot()))
                throw new IllegalArgumentException("material image has a stale epoch or foreign input address");
            before.add(new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), binding.quantity()));
            int remaining = binding.quantity() - decrements.getOrDefault(binding.address(), 0);
            if (remaining < 0) throw new IllegalArgumentException("recipe decrement exceeds its witnessed stack");
            if (remaining > 0) after.add(new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), remaining));
        }
        if (epoch == 0) throw new IllegalArgumentException("material image lacks physical input authority");
        return new ProductionStationMaterialImage(epoch, List.copyOf(before), List.copyOf(after));
    }
}
