package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;
public final class ExtractionPhysicalAuthority {
    private ExtractionPhysicalAuthority() { }
    public static boolean pendingForContainer(FrontierWorldState state, SubjectId container) {
        return state.extractionSites().work().values().stream().anyMatch(job -> job.pending().isPresent()
                && !(job.pending().orElseThrow() instanceof ExtractionPhysicalStep.BlockWork)
                && ExtractionWorkAuthority.site(state, job).containerId().equals(container));
    }
    public static List<ContainerSlotClaim> slotClaims(ExtractionSiteState sites) {
        var result = new ArrayList<ContainerSlotClaim>();
        for (var job : sites.work().values()) {
            var pending = job.pending().orElse(null);
            if (pending instanceof ExtractionPhysicalStep.Equipment equipment)
                result.add(new ContainerSlotClaim(new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.EXTRACTION, job.id()), equipment.slot()));
            else if (pending instanceof ExtractionPhysicalStep.Cargo cargo)
                result.add(new ContainerSlotClaim(new ContainerSlotClaim.Owner(ContainerSlotClaim.Family.EXTRACTION, job.id()),
                        new InventoryCustody.ContainerSlot(sites.deposits().get(job.siteId()).site().containerId(), cargo.transfer().destinationSlot())));
        }
        return List.copyOf(result);
    }
}
