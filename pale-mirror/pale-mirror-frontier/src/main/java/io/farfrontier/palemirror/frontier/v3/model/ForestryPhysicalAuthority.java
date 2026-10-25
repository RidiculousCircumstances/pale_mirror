package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import java.util.*;

/** Forest-issued exact physical fences, registered with shared container authority. */
public final class ForestryPhysicalAuthority {
    private ForestryPhysicalAuthority() { }
    public static boolean pendingForContainer(FrontierWorldState state, SubjectId container) {
        return state.forest().work().values().stream().anyMatch(job -> job.pending().isPresent()
                && ForestryServiceAccess.needsAccess(job) && ForestryServiceAccess.identity(job).pointId().equals(container));
    }
    public static List<ContainerSlotClaim> slotClaims(ForestState forest) {
        var claims = new ArrayList<ContainerSlotClaim>();
        for (var job : forest.work().values()) {
            var owner = new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.FORESTRY, job.id());
            if (job.pending().orElse(null) instanceof ForestryPhysicalStep.Equipment equipment)
                claims.add(new ContainerSlotClaim(owner, equipment.transfer().slot()));
            else if (job.pending().orElse(null) instanceof ForestryPhysicalStep.Cargo cargo)
                claims.add(new ContainerSlotClaim(owner, new InventoryCustody.ContainerSlot(job.storageTarget().containerId(), cargo.transfer().destinationSlot())));
        }
        return List.copyOf(claims);
    }
}
