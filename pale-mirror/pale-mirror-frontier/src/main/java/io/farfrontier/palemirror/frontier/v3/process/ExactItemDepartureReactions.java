package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.util.function.Supplier;

/** Transactional fact subscribers. All declared consumers receive the fact; no owner is inferred from the item or a job scan. */
final class ExactItemDepartureReactions {
    enum Owner { FORESTRY }
    interface Consumer {
        Owner owner();
        FrontierWorldState beforeInventory(FrontierWorldState state, SubjectId item, ExactInventory successor, long tick);
        List<ScheduledAction> retiredSchedules(FrontierWorldState before, FrontierWorldState after, Supplier<List<ScheduledAction>> pending);
    }
    private static final List<Consumer> CURRENT = closed(List.of(new ForestryToolDeparture()));
    static List<Consumer> closed(List<Consumer> consumers) {
        var owners = EnumSet.noneOf(Owner.class);
        for (var consumer : consumers) if (!owners.add(Objects.requireNonNull(consumer.owner())))
            throw new IllegalArgumentException("duplicate exact departure fact consumer");
        if (!owners.equals(EnumSet.allOf(Owner.class))) throw new IllegalArgumentException("missing exact departure fact consumer");
        return List.copyOf(consumers);
    }
    static FrontierWorldState beforeInventory(FrontierWorldState state, SubjectId item, ExactInventory successor, long tick) {
        var next = state;
        for (var consumer : CURRENT) next = Objects.requireNonNull(consumer.beforeInventory(next, item, successor, tick));
        return next;
    }
    static List<ScheduledAction> retiredSchedules(FrontierWorldState before, FrontierWorldState after, Supplier<List<ScheduledAction>> pending) {
        return CURRENT.stream().flatMap(consumer -> consumer.retiredSchedules(before, after, pending).stream()).distinct().toList();
    }
    private ExactItemDepartureReactions() { }
}
