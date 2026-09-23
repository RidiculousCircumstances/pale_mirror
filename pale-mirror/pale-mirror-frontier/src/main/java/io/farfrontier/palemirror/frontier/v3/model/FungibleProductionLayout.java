package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.TreeMap;

/** Bounded same-container recipe layout; preserves every unconsumed slot and exact item. */
public record FungibleProductionLayout(SubjectId containerId, SubjectId accountId, long authorityEpoch,
        Map<Integer, ReferenceContainerCustody.ProjectedFungibleSlot> before,
        Map<Integer, ReferenceContainerCustody.ProjectedFungibleSlot> after) {
    public FungibleProductionLayout {
        java.util.Objects.requireNonNull(containerId, "production container"); java.util.Objects.requireNonNull(accountId, "production account");
        before = Map.copyOf(before); after = Map.copyOf(after);
        if (authorityEpoch < 1 || before.isEmpty() || after.isEmpty() || before.size() > 54 || after.size() > 54
                || before.keySet().stream().anyMatch(slot -> slot < 0 || slot >= 54)
                || after.keySet().stream().anyMatch(slot -> slot < 0 || slot >= 54)) {
            throw new IllegalArgumentException("production layout is outside its bounded container epoch");
        }
    }

    public static FungibleProductionLayout plan(FrontierWorldState state, ProductionJob job) {
        if (!(job.inputHold() instanceof ProductionInputHold.FungibleBound held)
                || !FrontierProductionWorkSceneSupport.hasPhysicalInput(state, job)) {
            throw new IllegalArgumentException("production layout has no current bound input");
        }
        SubjectId container = FrontierWorldState.depotId(job.settlementId());
        int capacity = state.inventory().containers().get(container).slotCount();
        var bindings = new TreeMap<Integer, PhysicalStackBinding>();
        for (var binding : state.inventory().fungibleResources().bindings().values()) {
            if (!binding.accountId().equals(held.accountId())) continue;
            if (!(binding.address() instanceof PhysicalStackAddress.ContainerSlot slot) || !slot.slot().containerId().equals(container)
                    || slot.slot().slot() >= capacity || state.inventory().itemAt(container, slot.slot().slot()).isPresent()
                    || bindings.putIfAbsent(slot.slot().slot(), binding) != null) {
                throw new IllegalArgumentException("production input layout overlaps an unavailable slot");
            }
        }
        var before = new TreeMap<Integer, ReferenceContainerCustody.ProjectedFungibleSlot>();
        bindings.forEach((slot, binding) -> before.put(slot, new ReferenceContainerCustody.ProjectedFungibleSlot(binding.itemKind(), binding.quantity())));
        var after = new TreeMap<>(before);
        int required = job.outputCount();
        for (var entry : bindings.entrySet()) {
            var binding = entry.getValue();
            int take = Math.min(required, binding.lotQuantities().getOrDefault(held.itemId(), 0));
            if (take == 0) continue;
            int remaining = binding.quantity() - take;
            if (remaining == 0) after.remove(entry.getKey());
            else after.put(entry.getKey(), new ReferenceContainerCustody.ProjectedFungibleSlot(binding.itemKind(), remaining));
            required -= take;
        }
        if (required != 0) throw new IllegalArgumentException("production layout lacks its retained input lot quantity");
        int output = job.outputCount();
        for (int slot = 0; slot < capacity && output > 0; slot++) {
            if (state.inventory().itemAt(container, slot).isPresent()) continue;
            var existing = after.get(slot);
            if (existing != null && !existing.itemKind().equals(job.outputItemKind())) continue;
            int quantity = existing == null ? 0 : existing.quantity();
            int added = Math.min(64 - quantity, output);
            after.put(slot, new ReferenceContainerCustody.ProjectedFungibleSlot(job.outputItemKind(), quantity + added));
            output -= added;
        }
        if (output != 0) throw new IllegalArgumentException("production output has no free physical capacity");
        return new FungibleProductionLayout(container, held.accountId(), held.authorityEpoch(), before, after);
    }
}
