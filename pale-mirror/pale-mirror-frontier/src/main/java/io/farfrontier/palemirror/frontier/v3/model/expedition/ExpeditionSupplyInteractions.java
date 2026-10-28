package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Supply-owned immutable interaction projection. No receipt, claim or body authority. */
final class ExpeditionSupplyInteractions {
    private record Image(Map<SubjectId, Set<ActorItemSlot>> slots, Map<SubjectId, Set<SubjectId>> actors,
                         Set<SubjectId> containers) { }
    private static final ImmutableResultView<String, Image> IMAGES = new ImmutableResultView<>(4);
    private static Image view(FrontierWorldState state) {
        var missions = state.shipments().missions(); var surfaces = state.inventory().surfaces();
        return IMAGES.get(List.of(missions, surfaces), "supply-interactions", () -> {
            var slots = new HashMap<SubjectId, Set<ActorItemSlot>>();
            var actors = new HashMap<SubjectId, Set<SubjectId>>();
            var containers = new HashSet<SubjectId>();
            for (var mission : missions.values()) {
                mission.supplies().ifPresent(load -> load.allocations().forEach(allocation -> {
                    if (!allocation.loaded() && !allocation.withdrawn())
                        slots.computeIfAbsent(allocation.actorId(), ignored -> new HashSet<>()).add(allocation.slot());
                    if (allocation.pending().isEmpty()) return;
                    actors.computeIfAbsent(allocation.actorId(), ignored -> new HashSet<>()).add(mission.id());
                    containers.add(allocation.source().containerId());
                    if (allocation.slot() instanceof ActorItemSlot.AttachedStorage storage) containers.add(storage.containerId());
                }));
                mission.replenishment().ifPresent(trade -> {
                    slots.computeIfAbsent(trade.actorId(), ignored -> new HashSet<>()).add(trade.slot());
                    if (trade.pending().isEmpty()) return;
                    actors.computeIfAbsent(trade.actorId(), ignored -> new HashSet<>()).add(mission.id());
                    containers.add(trade.containerId());
                    var surface = Objects.requireNonNull(surfaces.get(trade.containerId()), "pending replenishment lost its container surface");
                    if (surface.location() instanceof ContainerLocation.Mobile mobile)
                        actors.computeIfAbsent(mobile.actorId(), ignored -> new HashSet<>()).add(mission.id());
                });
            }
            slots.replaceAll((id, values) -> Set.copyOf(values)); actors.replaceAll((id, values) -> Set.copyOf(values));
            return new Image(ImmutableLookupMap.copyOf(slots), ImmutableLookupMap.copyOf(actors), Set.copyOf(containers));
        });
    }
    static boolean pendingForContainer(FrontierWorldState state, SubjectId container) { return view(state).containers().contains(container); }
    static Optional<SubjectId> pendingOwnerForActor(FrontierWorldState state, SubjectId actor) {
        var owners = view(state).actors().getOrDefault(actor, Set.of());
        if (owners.size() > 1) throw new IllegalArgumentException("actor has competing expedition load effects");
        return owners.stream().findFirst();
    }
    static Set<ActorItemSlot> reservedSlots(FrontierWorldState state, SubjectId actor) {
        return view(state).slots().getOrDefault(Objects.requireNonNull(actor), Set.of());
    }
    private ExpeditionSupplyInteractions() { }
}
