package io.farfrontier.palemirror.frontier.v3.model;
import java.util.Set;
/** Agriculture may deposit into nearby production buffers or ordinary warehouses; storage owns filters/capacity. */
public final class HarvestStoragePolicy {
    public static final Set<ResourceStorageRole> ROLES=Set.of(ResourceStorageRole.WAREHOUSE,ResourceStorageRole.LOCAL_BUFFER);
    private HarvestStoragePolicy(){}
}
