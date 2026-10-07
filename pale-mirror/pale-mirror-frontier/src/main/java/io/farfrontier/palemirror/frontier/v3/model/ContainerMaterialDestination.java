package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Transient physical placement over the shared declared container image, never a resource identity. */
public record ContainerMaterialDestination(int slot, int before, Map<SubjectId, Integer> lots) {
    public ContainerMaterialDestination { lots = Map.copyOf(lots); }

    public static Optional<ContainerMaterialDestination> select(FrontierWorldState state, SubjectId container,
            String kind, Map<SubjectId, Integer> lots, int allowed, Optional<SubjectId> completingOwner) {
        return select(state, container, kind, lots, allowed, completingOwner, false);
    }
    public static Optional<ContainerMaterialDestination> selectWhole(FrontierWorldState state, SubjectId container,
            String kind, Map<SubjectId, Integer> lots, Optional<SubjectId> completingOwner) {
        return select(state, container, kind, lots, lots.values().stream().mapToInt(Integer::intValue).sum(), completingOwner, true);
    }
    private static Optional<ContainerMaterialDestination> select(FrontierWorldState state, SubjectId container,
            String kind, Map<SubjectId, Integer> lots, int allowed, Optional<SubjectId> completingOwner, boolean whole) {
        int quantity = lots.values().stream().mapToInt(Integer::intValue).sum();
        if (allowed < 0 || allowed > quantity) throw new IllegalArgumentException("placement allowance exceeds declared resources");
        while (allowed > 0 && !ContainerStorageAdmission.receive(state, container, kind, allowed, completingOwner)) allowed--;
        if (whole && allowed != quantity) return Optional.empty();
        var record = state.inventory().containers().get(container);
        if (record == null) throw new IllegalArgumentException("placement has no declared container");
        for (int slot = 0; allowed > 0 && slot < record.slotCount(); slot++) {
            if (state.reservedContainerSlots(container).contains(slot)
                    || state.inventory().occupiedSlots().containsKey(new InventoryCustody.ContainerSlot(container, slot))) continue;
            var stack = ReferenceContainerCustody.expectedFungibleSlot(state, container, slot);
            if (stack.isPresent() && !stack.orElseThrow().itemKind().equals(kind)) continue;
            int before = stack.map(ReferenceContainerCustody.ProjectedFungibleSlot::quantity).orElse(0);
            int take = Math.min(allowed, 64 - before);
            if (take < 1 || whole && take != quantity) continue;
            var portion = new LinkedHashMap<SubjectId, Integer>();
            for (var entry : lots.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                int moved = Math.min(take, entry.getValue());
                if (moved > 0) portion.put(entry.getKey(), moved);
                take -= moved;
            }
            return Optional.of(new ContainerMaterialDestination(slot, before, portion));
        }
        return Optional.empty();
    }
}
