package io.farfrontier.palemirror.frontier.reference;

/** Exact typed vocabulary for Python {@code FrontCampaignKind}. */
public enum ReferenceFrontCampaignKind {
    SECTOR_CLEARANCE("sector_clearance"),
    CORDON("cordon"),
    ROUTE_SECURITY("route_security"),
    NEST_ISOLATION("nest_isolation"),
    ORGAN_RAID("organ_raid"),
    RELIEF("relief");

    private final String id;

    ReferenceFrontCampaignKind(String id) { this.id = id; }

    public String id() { return id; }
}
