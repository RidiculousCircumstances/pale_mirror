package io.farfrontier.palemirror.domain;

import java.util.List;

/** Canonical history rule separating genuine recovery aftermath from ordinary prosperity. */
public final class DevelopmentOpportunityEligibility {
    private DevelopmentOpportunityEligibility() { }

    /**
     * A recovery investment may be presented only when this community first
     * entered a real crisis and later stabilized before the development plan
     * was created. Stable fresh-world surplus remains autonomous prosperity.
     */
    public static boolean isRecoveryOpportunity(WorldState state, DomainEvent planned) {
        if (planned.type() != DomainEventType.SETTLEMENT_DEVELOPMENT_PLANNED) return false;
        List<DomainEvent> history = state.history();
        int plannedIndex = indexOf(history, planned.eventId());
        if (plannedIndex < 0) return false;
        int crisisIndex = latestBefore(history, plannedIndex, planned.subject(),
                DomainEventType.SETTLEMENT_CRISIS_DETECTED);
        if (crisisIndex < 0) return false;
        int stabilizedIndex = latestBefore(history, plannedIndex, planned.subject(),
                DomainEventType.SETTLEMENT_STABILIZED);
        return stabilizedIndex > crisisIndex;
    }

    private static int latestBefore(List<DomainEvent> history, int limit,
                                    WorldObjectId subject, DomainEventType type) {
        for (int index = limit - 1; index >= 0; index--) {
            DomainEvent event = history.get(index);
            if (event.subject().equals(subject) && event.type() == type) return index;
        }
        return -1;
    }

    private static int indexOf(List<DomainEvent> history, String eventId) {
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index).eventId().equals(eventId)) return index;
        }
        return -1;
    }
}
