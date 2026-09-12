package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Durable exact evidence for one partial player, hopper, drop, or pickup handoff. */
public record FungibleResourceHandoffObserved(SubjectId sourceAccountId, CustodyAccount destinationAccount,
                                              long sourceEpoch, long destinationEpoch,
                                              Map<SubjectId, Integer> lotQuantities, Map<SubjectId, Integer> claimQuantities,
                                              List<PhysicalStackBinding> remainingSource, List<PhysicalStackBinding> destinationBindings,
                                              java.util.Set<SubjectId> forfeitedClaimIds, String playerSaveFence)
        implements FrontierPayload {
    public FungibleResourceHandoffObserved(SubjectId sourceAccountId, CustodyAccount destinationAccount, long sourceEpoch, long destinationEpoch,
                                            Map<SubjectId, Integer> lotQuantities, Map<SubjectId, Integer> claimQuantities,
                                            List<PhysicalStackBinding> remainingSource, List<PhysicalStackBinding> destinationBindings) {
        this(sourceAccountId, destinationAccount, sourceEpoch, destinationEpoch, lotQuantities, claimQuantities,
                remainingSource, destinationBindings, java.util.Set.of(), "");
    }

    public FungibleResourceHandoffObserved(SubjectId sourceAccountId, CustodyAccount destinationAccount, long sourceEpoch, long destinationEpoch,
                                            Map<SubjectId, Integer> lotQuantities, Map<SubjectId, Integer> claimQuantities,
                                            List<PhysicalStackBinding> remainingSource, List<PhysicalStackBinding> destinationBindings,
                                            java.util.Set<SubjectId> forfeitedClaimIds) {
        this(sourceAccountId, destinationAccount, sourceEpoch, destinationEpoch, lotQuantities, claimQuantities,
                remainingSource, destinationBindings, forfeitedClaimIds, "");
    }

    public FungibleResourceHandoffObserved {
        Objects.requireNonNull(sourceAccountId, "handoff source account"); Objects.requireNonNull(destinationAccount, "handoff destination account");
        if (sourceEpoch < 1 || destinationEpoch < 1) throw new IllegalArgumentException("handoff authority epochs must be positive");
        lotQuantities = quantities(lotQuantities, "lot", false); claimQuantities = quantities(claimQuantities, "claim", true);
        remainingSource = remainingSource.stream().sorted(java.util.Comparator.comparing(PhysicalStackBinding::id)).toList();
        destinationBindings = destinationBindings.stream().sorted(java.util.Comparator.comparing(PhysicalStackBinding::id)).toList();
        forfeitedClaimIds = java.util.Set.copyOf(Objects.requireNonNull(forfeitedClaimIds, "forfeited claims"));
        if (remainingSource.stream().anyMatch(Objects::isNull) || destinationBindings.isEmpty() || destinationBindings.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("handoff requires current non-null destination binding evidence");
        }
        if (!forfeitedClaimIds.isEmpty() && !forfeitedClaimIds.equals(claimQuantities.keySet())) {
            throw new IllegalArgumentException("handoff forfeiture must name exactly its moved claim portions");
        }
        playerSaveFence = Objects.requireNonNull(playerSaveFence, "player save fence");
        if (!playerSaveFence.isEmpty()) {
            if (!(destinationAccount.custody() instanceof ResourceCustody.Player)
                    || destinationBindings.size() != 1 || !(destinationBindings.getFirst().address() instanceof PhysicalStackAddress.PlayerSlot)) {
                throw new IllegalArgumentException("player save fence requires one player destination binding");
            }
            try { java.util.UUID.fromString(playerSaveFence); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("player save fence must be a UUID", invalid); }
        }
    }
    @Override public String type() { return "frontier.fungible_resource_handoff_observed"; }

    /** Binds a physical departure of a reserved portion to the one owning-work forfeiture path. */
    public FungibleResourceHandoffObserved forfeitMovedClaims() {
        return claimQuantities.isEmpty() ? this : new FungibleResourceHandoffObserved(sourceAccountId, destinationAccount, sourceEpoch,
                destinationEpoch, lotQuantities, claimQuantities, remainingSource, destinationBindings, claimQuantities.keySet(), playerSaveFence);
    }

    /** Replays the same observed layout after its named allocations have been atomically released. */
    public FungibleResourceHandoffObserved withoutForfeitedClaims() {
        if (forfeitedClaimIds.isEmpty()) return this;
        return new FungibleResourceHandoffObserved(sourceAccountId,
                new CustodyAccount(destinationAccount.id(), destinationAccount.custody(), destinationAccount.lotQuantities(), Map.of()),
                sourceEpoch, destinationEpoch, lotQuantities, Map.of(), without(forfeitedClaimIds, remainingSource),
                without(forfeitedClaimIds, destinationBindings), java.util.Set.of(), playerSaveFence);
    }

    /** Binds one newly observed player handoff to the later player-save durability fence. */
    public FungibleResourceHandoffObserved withPlayerSaveFence(java.util.UUID fence) {
        Objects.requireNonNull(fence, "player save fence");
        return new FungibleResourceHandoffObserved(sourceAccountId, destinationAccount, sourceEpoch, destinationEpoch,
                lotQuantities, claimQuantities, remainingSource, destinationBindings, forfeitedClaimIds, fence.toString());
    }

    private static List<PhysicalStackBinding> without(java.util.Set<SubjectId> claimIds, List<PhysicalStackBinding> values) {
        return values.stream().map(binding -> {
            java.util.Map<SubjectId, Integer> claims = new java.util.HashMap<>(binding.claimQuantities()); claimIds.forEach(claims::remove);
            return new PhysicalStackBinding(binding.id(), binding.accountId(), binding.address(), binding.authorityEpoch(), binding.itemKind(),
                    binding.lotQuantities(), claims, binding.playerSaveFence());
        }).toList();
    }

    private static Map<SubjectId, Integer> quantities(Map<SubjectId, Integer> values, String label, boolean emptyAllowed) {
        if (values == null || (!emptyAllowed && values.isEmpty())) throw new IllegalArgumentException("handoff " + label + " quantities are required");
        Map<SubjectId, Integer> copy = Map.copyOf(values);
        if (copy.values().stream().anyMatch(value -> value == null || value < 1 || value > ResourceLot.MAX_QUANTITY)) {
            throw new IllegalArgumentException("handoff " + label + " quantity is invalid");
        }
        return copy;
    }
}
