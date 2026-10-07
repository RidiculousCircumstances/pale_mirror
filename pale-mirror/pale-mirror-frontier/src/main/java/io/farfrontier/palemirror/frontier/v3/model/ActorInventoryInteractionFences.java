package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/** Inventory interaction owners declare their fences; shared activity/body code knows no job phases. */
public final class ActorInventoryInteractionFences {
    private static final List<BiFunction<FrontierWorldState, SubjectId, Optional<SubjectId>>> OWNERS = List.of(
            ExpeditionSupplyAuthority::pendingOwnerForActor, ReferenceContainerCustody::pendingOwnerForActor);
    private ActorInventoryInteractionFences() { }
    public static boolean pending(FrontierWorldState state, SubjectId actor) {
        return pendingOwner(state, actor).isPresent();
    }
    public static Optional<SubjectId> pendingOwner(FrontierWorldState state, SubjectId actor) {
        var owners = OWNERS.stream().flatMap(owner -> owner.apply(state, actor).stream()).toList();
        if (owners.size() > 1) throw new IllegalArgumentException("actor has competing physical inventory interaction owners");
        return owners.stream().findFirst();
    }
}
