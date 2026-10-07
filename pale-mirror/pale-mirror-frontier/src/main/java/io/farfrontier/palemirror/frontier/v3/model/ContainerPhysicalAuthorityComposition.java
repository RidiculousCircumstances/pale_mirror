package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.function.BiPredicate;

/** Closed composition of owner-supplied before-effect fences; never inspects a family's work. */
public final class ContainerPhysicalAuthorityComposition {
    private static final List<BiPredicate<FrontierWorldState, SubjectId>> OWNERS = List.of(
            BakeryPhysicalAuthority::pendingForContainer, ResidentMealPhysicalAuthority::pendingForContainer,
            ShipmentPhysicalAuthority::pendingForContainer,
            io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority::pendingForContainer);
    private ContainerPhysicalAuthorityComposition() { }
    public static boolean pending(FrontierWorldState state, SubjectId container) {
        return OWNERS.stream().anyMatch(owner -> owner.test(state, container));
    }
}
