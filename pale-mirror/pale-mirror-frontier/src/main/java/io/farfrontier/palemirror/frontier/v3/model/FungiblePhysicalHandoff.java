package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the one durable payload for a partial departure from a currently bound Vanilla stack.
 * It has no knowledge of Minecraft containers or actors: adapters supply only a verified target
 * address.  That keeps split/merge arithmetic and claim conservation in the canonical model.
 */
public final class FungiblePhysicalHandoff {
    private FungiblePhysicalHandoff() { }

    /** Creates the destination account from the exact portion removed from one observed stack. */
    public static FungibleResourceHandoffObserved departToNew(FungibleResourceLedger ledger, SubjectId sourceAccountId,
                                                              long sourceEpoch, PhysicalStackBinding sourceBinding,
                                                              int remainingQuantity, SubjectId destinationId,
                                                              ResourceCustody destinationCustody, long destinationEpoch,
                                                              PhysicalStackAddress destinationAddress) {
        Objects.requireNonNull(destinationId, "destination account identity"); Objects.requireNonNull(destinationCustody, "destination custody");
        if (remainingQuantity < 0 || remainingQuantity >= sourceBinding.quantity()) {
            throw new IllegalArgumentException("fungible departure has no positive moved portion");
        }
        Map<SubjectId, Integer> retainedLots = first(sourceBinding.lotQuantities(), remainingQuantity);
        Map<SubjectId, Integer> movedLots = subtract(sourceBinding.lotQuantities(), retainedLots);
        int retainedClaimQuantity = Math.min(remainingQuantity, sourceBinding.claimQuantities().values().stream().mapToInt(Integer::intValue).sum());
        Map<SubjectId, Integer> movedClaims = subtract(sourceBinding.claimQuantities(), first(sourceBinding.claimQuantities(), retainedClaimQuantity));
        return depart(ledger, sourceAccountId, sourceEpoch, sourceBinding, remainingQuantity,
                new CustodyAccount(destinationId, destinationCustody, movedLots, movedClaims), destinationEpoch, destinationAddress);
    }

    public static FungibleResourceHandoffObserved depart(FungibleResourceLedger ledger, SubjectId sourceAccountId,
                                                         long sourceEpoch, PhysicalStackBinding sourceBinding,
                                                         int remainingQuantity, CustodyAccount destination,
                                                         long destinationEpoch, PhysicalStackAddress destinationAddress) {
        Objects.requireNonNull(ledger, "fungible ledger"); Objects.requireNonNull(sourceAccountId, "source account");
        Objects.requireNonNull(sourceBinding, "source binding"); Objects.requireNonNull(destination, "destination account");
        Objects.requireNonNull(destinationAddress, "destination physical address");
        CustodyAccount source = ledger.accounts().get(sourceAccountId);
        if (source == null || sourceEpoch < 1 || destinationEpoch < 1 || remainingQuantity < 0
                || remainingQuantity >= sourceBinding.quantity() || !sourceBinding.accountId().equals(source.id())
                || sourceBinding.authorityEpoch() != sourceEpoch || ledger.accounts().containsKey(destination.id())) {
            throw new IllegalArgumentException("fungible departure has no current distinct custody boundary");
        }
        List<PhysicalStackBinding> current = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(source.id()))
                .sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
        if (current.isEmpty() || current.stream().anyMatch(binding -> binding.authorityEpoch() != sourceEpoch)
                || !current.contains(sourceBinding)) {
            throw new IllegalArgumentException("fungible departure has no current source binding");
        }
        int transferredQuantity = sourceBinding.quantity() - remainingQuantity;
        Map<SubjectId, Integer> retainedLots = first(sourceBinding.lotQuantities(), remainingQuantity);
        Map<SubjectId, Integer> movedLots = subtract(sourceBinding.lotQuantities(), retainedLots);
        Map<SubjectId, Integer> retainedClaims = first(sourceBinding.claimQuantities(), Math.min(remainingQuantity, sourceBinding.claimQuantities().values().stream().mapToInt(Integer::intValue).sum()));
        Map<SubjectId, Integer> movedClaims = subtract(sourceBinding.claimQuantities(), retainedClaims);
        if (!destination.lotQuantities().equals(movedLots) || !destination.claimQuantities().equals(movedClaims)
                || destination.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() != transferredQuantity
                || !sourceBinding.itemKind().equals(singleKind(ledger, movedLots))) {
            throw new IllegalArgumentException("fungible departure destination does not retain its exact moved portion");
        }
        List<PhysicalStackBinding> remaining = new ArrayList<>();
        for (PhysicalStackBinding binding : current) {
            if (!binding.id().equals(sourceBinding.id())) remaining.add(binding);
            else if (remainingQuantity > 0) remaining.add(new PhysicalStackBinding(binding.id(), binding.accountId(), binding.address(), binding.authorityEpoch(),
                    binding.itemKind(), retainedLots, retainedClaims, binding.playerSaveFence()));
        }
        SubjectId bindingId = PhysicalStackBinding.generatedId(destination.id(), destinationEpoch, 0);
        PhysicalStackBinding arrived = new PhysicalStackBinding(bindingId, destination.id(), destinationAddress, destinationEpoch,
                sourceBinding.itemKind(), movedLots, movedClaims);
        return new FungibleResourceHandoffObserved(source.id(), destination, sourceEpoch, destinationEpoch, movedLots, movedClaims,
                remaining, List.of(arrived));
    }

    /** Folds one vanished player/world binding back into a currently bound destination stack. */
    public static FungibleResourceHandoffObserved returnToExisting(FungibleResourceLedger ledger, SubjectId sourceAccountId,
                                                                   long sourceEpoch, PhysicalStackBinding sourceBinding,
                                                                   SubjectId destinationAccountId, long destinationEpoch,
                                                                   PhysicalStackBinding destinationBinding) {
        Objects.requireNonNull(ledger, "fungible ledger"); Objects.requireNonNull(sourceBinding, "source binding");
        Objects.requireNonNull(destinationBinding, "destination binding");
        CustodyAccount source = ledger.accounts().get(sourceAccountId); CustodyAccount destination = ledger.accounts().get(destinationAccountId);
        if (source == null || destination == null || source.id().equals(destination.id()) || sourceEpoch < 1 || destinationEpoch < 1
                || !sourceBinding.accountId().equals(source.id()) || !destinationBinding.accountId().equals(destination.id())
                || sourceBinding.authorityEpoch() != sourceEpoch || destinationBinding.authorityEpoch() != destinationEpoch
                || !sourceBinding.itemKind().equals(destinationBinding.itemKind())) {
            throw new IllegalArgumentException("fungible return has no compatible current custody boundary");
        }
        List<PhysicalStackBinding> sourceCurrent = current(ledger, source.id(), sourceEpoch);
        List<PhysicalStackBinding> destinationCurrent = current(ledger, destination.id(), destinationEpoch);
        if (!sourceCurrent.contains(sourceBinding) || !destinationCurrent.contains(destinationBinding)) {
            throw new IllegalArgumentException("fungible return has no current source or destination binding");
        }
        Map<SubjectId, Integer> finalLots = add(destinationBinding.lotQuantities(), sourceBinding.lotQuantities());
        Map<SubjectId, Integer> finalClaims = add(destinationBinding.claimQuantities(), sourceBinding.claimQuantities());
        List<PhysicalStackBinding> remainingSource = sourceCurrent.stream().filter(binding -> !binding.id().equals(sourceBinding.id())).toList();
        List<PhysicalStackBinding> destinationBindings = new ArrayList<>();
        for (PhysicalStackBinding binding : destinationCurrent) {
            destinationBindings.add(binding.id().equals(destinationBinding.id()) ? new PhysicalStackBinding(binding.id(), binding.accountId(),
                    binding.address(), binding.authorityEpoch(), binding.itemKind(), finalLots, finalClaims, binding.playerSaveFence()) : binding);
        }
        return new FungibleResourceHandoffObserved(source.id(), destination.withQuantities(
                add(destination.lotQuantities(), sourceBinding.lotQuantities()), add(destination.claimQuantities(), sourceBinding.claimQuantities())),
                sourceEpoch, destinationEpoch, sourceBinding.lotQuantities(), sourceBinding.claimQuantities(), remainingSource, destinationBindings);
    }

    /** A witnessed player return may split and merge into any exact observed container layout. */
    public static FungibleResourceHandoffObserved returnObserved(FungibleResourceLedger ledger,
            PhysicalStackBinding source, int remainingQuantity, SubjectId destinationId,
            SubjectId containerId, long destinationEpoch, List<FungiblePhysicalObservation.Stack> observed) {
        if (!(source.address() instanceof PhysicalStackAddress.PlayerSlot)
                || !source.claimQuantities().isEmpty() || remainingQuantity < 0 || remainingQuantity >= source.quantity()
                || !current(ledger, source.accountId(), source.authorityEpoch()).contains(source))
            throw new IllegalArgumentException("player return lacks its exact current unclaimed source");
        CustodyAccount destination = ledger.accounts().get(destinationId);
        if (destination != null && !destination.custody().equals(new ResourceCustody.Container(containerId)))
            throw new IllegalArgumentException("player return has a foreign destination");
        Map<SubjectId, Integer> retained = first(source.lotQuantities(), remainingQuantity);
        Map<SubjectId, Integer> moved = subtract(source.lotQuantities(), retained);
        CustodyAccount after = new CustodyAccount(destinationId, new ResourceCustody.Container(containerId),
                add(destination == null ? Map.of() : destination.lotQuantities(), moved),
                destination == null ? Map.of() : destination.claimQuantities());
        List<PhysicalStackBinding> remaining = new ArrayList<>();
        for (var binding : current(ledger, source.accountId(), source.authorityEpoch())) {
            if (!binding.id().equals(source.id())) remaining.add(binding);
            else if (remainingQuantity > 0) remaining.add(new PhysicalStackBinding(binding.id(), binding.accountId(),
                    binding.address(), binding.authorityEpoch(), binding.itemKind(), retained, Map.of(), binding.playerSaveFence()));
        }
        var destinationBindings = FungiblePhysicalObservation.bindAccount(ledger, after, destinationEpoch, observed);
        return new FungibleResourceHandoffObserved(source.accountId(), after, source.authorityEpoch(), destinationEpoch,
                moved, Map.of(), remaining, destinationBindings);
    }

    private static Map<SubjectId, Integer> first(Map<SubjectId, Integer> source, int quantity) {
        if (quantity == 0) return Map.of();
        int remaining = quantity; Map<SubjectId, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<SubjectId, Integer> entry : source.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            int used = Math.min(remaining, entry.getValue());
            if (used > 0) result.put(entry.getKey(), used);
            remaining -= used;
            if (remaining == 0) return Map.copyOf(result);
        }
        throw new IllegalArgumentException("fungible departure exceeds one source binding");
    }

    private static Map<SubjectId, Integer> subtract(Map<SubjectId, Integer> source, Map<SubjectId, Integer> retained) {
        Map<SubjectId, Integer> result = new LinkedHashMap<>();
        source.forEach((id, quantity) -> { int moved = quantity - retained.getOrDefault(id, 0); if (moved > 0) result.put(id, moved); });
        return Map.copyOf(result);
    }

    private static Map<SubjectId, Integer> add(Map<SubjectId, Integer> left, Map<SubjectId, Integer> right) {
        Map<SubjectId, Integer> result = new LinkedHashMap<>(left); right.forEach((id, quantity) -> result.merge(id, quantity, Integer::sum));
        return Map.copyOf(result);
    }

    private static List<PhysicalStackBinding> current(FungibleResourceLedger ledger, SubjectId account, long epoch) {
        List<PhysicalStackBinding> result = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(account))
                .sorted(Comparator.comparing(PhysicalStackBinding::id)).toList();
        if (result.isEmpty() || result.stream().anyMatch(binding -> binding.authorityEpoch() != epoch)) {
            throw new IllegalArgumentException("fungible return has stale physical authority");
        }
        return result;
    }

    private static String singleKind(FungibleResourceLedger ledger, Map<SubjectId, Integer> quantities) {
        return quantities.keySet().stream().map(ledger.lots()::get).map(ResourceLot::itemKind).distinct().reduce((left, right) -> {
            throw new IllegalArgumentException("fungible departure cannot materialize mixed kinds into one stack");
        }).orElseThrow(() -> new IllegalArgumentException("fungible departure has no moved lot"));
    }
}
