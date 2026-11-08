package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.List;

/** Applies complete modeled transactions through registered production owners; no physical outcome evidence. */
final class ModelEventDriver {
    long sequence;
    FrontierWorldState apply(FrontierWorldState state, List<ProposedEvent> events, long tick) {
        var result = FrontierWorldState.duringUnpublishedTransition(() -> {
            var next = state;
            for (var event : events) {
                if (event.payload() instanceof ScheduleEffect) continue;
                var suffix = Long.toString(++sequence);
                next = FrontierWorldRuntimeDefinition.reduce(next, new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                        new EventId("event:seated-" + suffix), new TransactionId("transaction:seated-" + suffix), state.bootstrap().worldId(),
                        new Revision(sequence), new SimInstant(tick), event.subject(), CauseChain.root(new CommandId("test:seated-meal")), event.payload()));
            }
            return next;
        });
        result.validateTransitionFrom(state);
        return result;
    }
}
