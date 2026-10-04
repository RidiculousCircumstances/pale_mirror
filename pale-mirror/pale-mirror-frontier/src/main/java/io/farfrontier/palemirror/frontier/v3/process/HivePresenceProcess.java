package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorPresenceStarted;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Hive policy issues purpose-free execution; it neither creates nor moves a physical body. */
public final class HivePresenceProcess {
    public static final String INITIALIZE = "frontier.hive.presence.initialize";
    private HivePresenceProcess() { }

    /** One finite bootstrap turn, not another periodic/per-actor retry stream. */
    public static ScheduledAction initialize(SubjectId hiveId) {
        return new ScheduledAction(new ScheduleId("schedule:hive-presence-"
                + hiveId.value().replace(':', '-') + "-1"), new SimInstant(1L), 0, hiveId, INITIALIZE, 1);
    }
    public static List<ProposedEvent> planInitialization(FrontierWorldState state, ScheduledAction action) {
        return planInitialization(state, action, action.dueAt().ticks());
    }
    public static List<ProposedEvent> planInitialization(FrontierWorldState state, ScheduledAction action, long currentTick) {
        var expected = initialize(state.bootstrap().hive().id());
        if (!action.equals(expected) || currentTick < action.dueAt().ticks())
            throw new IllegalArgumentException("foreign or premature hive presence initialization");
        var events = new ArrayList<>(planFree(state, action.subject(), currentTick));
        events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
        return List.copyOf(events);
    }

    /** Existing hive review also reconsiders released/new actors, without stealing retained purposes. */
    public static List<ProposedEvent> planFree(FrontierWorldState state, SubjectId hiveId, long atTick) {
        if (!state.bootstrap().hive().id().equals(hiveId) || atTick < 1L)
            throw new IllegalArgumentException("passive selection requires its exact hive and instant");
        return Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .sorted(Comparator.comparing(Bioform::id)).filter(profile -> {
                    if (!profile.hiveId().equals(hiveId)) throw new IllegalArgumentException("foreign bioform profile in hive policy");
                    var retained = state.actorExecutions().actors().get(profile.id());
                    return (retained == null || retained.current().isEmpty() && retained.suspended().isEmpty())
                            && HivePresencePolicy.permits(state, profile.id());
                }).map(profile -> new ProposedEvent(profile.id(), new ActorPresenceStarted(
                        state.actorExecutions().next(profile.id(), ActorActivityKind.PRESENCE, profile.id()), atTick)))
                .toList();
    }
}
