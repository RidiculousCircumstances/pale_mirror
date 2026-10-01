package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.List;

/** Effects supplied by physiology owners before a nutrition interval is retired atomically. */
@FunctionalInterface
public interface NutritionIntervalEffects {
    List<ProposedEvent> beforeRetirement(FrontierWorldState state, SubjectId resident, long tick);
}
