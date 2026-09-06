package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;

import java.util.Objects;

/** Durable conversion of one exact hive resource into one organ and one bioform identity. */
public record HiveGrowthJob(SubjectId id, SubjectId hiveId, SubjectId nestId, SubjectId consumedItemId, PhysicalIntentId consumptionIntentId,
                            HiveOrgan organ, Bioform bioform) {
    public HiveGrowthJob {
        Objects.requireNonNull(id, "hive growth job id"); Objects.requireNonNull(hiveId, "hive id");
        Objects.requireNonNull(nestId, "nest id"); Objects.requireNonNull(consumedItemId, "consumed item id"); Objects.requireNonNull(consumptionIntentId, "consumption intent id");
        Objects.requireNonNull(organ, "grown organ"); Objects.requireNonNull(bioform, "spawned bioform");
        if (organ.kind() == HiveOrganKind.STORE) throw new IllegalArgumentException("growth cannot create a store without its exact container");
        if (!hiveId.equals(organ.hiveId()) || !hiveId.equals(bioform.hiveId()) || !nestId.equals(organ.nestId()) || !nestId.equals(bioform.nestId())) {
            throw new IllegalArgumentException("hive growth outputs must belong to one named nest");
        }
    }
}
