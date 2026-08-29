package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierV3ResourceSiteExecutorTest {
    @Test
    void naturallyLoadedFieldIsNotBlockedByAnEarlierUnloadedPreparation() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:loaded-field-selection"), 77L));
        engine.advanceTo(new SimInstant(100L), new WorkBudget(64, 512));
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());

        var selected = FrontierV3ResourceSitePreparationSelection.nextLoaded(state,
                site -> site.id().value().equals("site:2-wheat-field")).orElseThrow();

        assertEquals("intent:site-prepare-2-wheat-field", selected.id().value(),
                "an unloaded lexicographically earlier field must defer without force-loading or blocking the loaded field");
    }
}
