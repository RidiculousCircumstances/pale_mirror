package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Closed read-model composition; operation owners declare their occupied facilities. */
public final class SettlementCommitmentComposition {
    public static final SettlementCommitmentAdmission ADMISSION = new SettlementCommitmentAdmission(List.of(
            state -> state.productionJobs().values().stream().map(ProductionJob::facilityId),
            state -> state.resourceSites().sites().values().stream().flatMap(site -> site.activeWork().stream())
                    .filter(ResourceSiteHarvestJob.class::isInstance).map(ResourceSiteHarvestJob.class::cast)
                    .map(job -> state.resourceSite(job.siteId()).facilityId())));
    private SettlementCommitmentComposition() { }
}
