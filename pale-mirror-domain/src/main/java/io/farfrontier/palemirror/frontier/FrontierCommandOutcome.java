package io.farfrontier.palemirror.frontier;

import java.util.List;

public record FrontierCommandOutcome(boolean accepted, List<FrontierEvent> events) {
    public FrontierCommandOutcome { events = List.copyOf(events); }
}
