package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestTargetRetargeted;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceSiteHarvestTargetRetargetedCodecTest {
    @Test void bothPhysicalAndColdSelectionFactsReplayExactly() {
        var codec = ResourceSitePayloadCodecs.harvestTargetRetargeted();
        for (var lease : java.util.List.of(Optional.<SceneLeaseId>empty(),
                Optional.of(new SceneLeaseId("lease:harvest-1")))) {
            var fact = new ResourceSiteHarvestTargetRetargeted(new SubjectId("site:1-wheat-field"),
                    new SubjectId("job:site-harvest-1"), new SubjectId("resident:1-1"), 7L,
                    2, 5, new ScheduleId("schedule:resource-site-harvest-cold-progress-site-harvest-1"),
                    900L, lease);
            assertEquals(fact, codec.decode(codec.encode(fact)));
        }
    }
}
