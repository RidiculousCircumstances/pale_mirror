package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/** Closed declaration composition. Common inventory does not inspect concrete mission stages. */
final class UnitInventorySlotReservations {
    private static final List<BiFunction<FrontierWorldState, SubjectId, Set<ActorItemSlot>>> OWNERS =
            List.of(ExpeditionSupplyAuthority::reservedSlots);
    private UnitInventorySlotReservations() { }
    static Set<ActorItemSlot> reserved(FrontierWorldState state, SubjectId actor) {
        var slots = new java.util.HashSet<ActorItemSlot>();
        for (var owner : OWNERS) for (var slot : owner.apply(state, actor))
            if (!slots.add(slot)) throw new IllegalArgumentException("competing future personal inventory allocations");
        return Set.copyOf(slots);
    }
}
