package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Executes one durable hive infection-expansion task, never an ownerless metabolism pulse. */
final class HiveInfectionProcess {
    /**
     * COLD infection advances at meaningful visible boundaries, not every five seconds.
     *
     * <p>One pulse changes one 4×4 cell by one eighth. At this cadence a cell takes four
     * minutes to bloom while a loaded materializer still presents the same canonical stages
     * immediately. This preserves a readable, background world process without producing a
     * high-frequency stream of otherwise invisible immutable COLD transactions.</p>
     */
    static final long COLD_PULSE_INTERVAL = 600L;
    private static final long PULSE_GAIN = 125_000L;

    private HiveInfectionProcess() { }

    static ScheduledAction task(StrategicTask task, int pulse, long dueAt) {
        if (task.kind() != StrategicTaskKind.SPREAD_INFECTION_CELL || pulse <= 0) throw new IllegalArgumentException("invalid hive infection task schedule");
        String id = task.id().value().replace(':', '-') + "-" + pulse;
        return new ScheduledAction(new ScheduleId("schedule:hive-infection-task-" + id), new SimInstant(dueAt), 0,
                task.id(), "frontier.hive.infection.task", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = state.strategicPlans().tasks().get(action.subject());
        if (task == null || task.kind() != StrategicTaskKind.SPREAD_INFECTION_CELL || !task.ownerId().equals(state.bootstrap().hive().id())) {
            // A strategic interception may terminate and compact an older expansion task
            // before this persisted due action reaches the scheduler.  The task lifecycle,
            // not the historical schedule entry, owns whether the pulse still exists.  The
            // kernel still needs the explicit cancellation so it cannot silently drop work.
            return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        }
        if (task.status() == StrategicTaskStatus.BLOCKED || task.status() == StrategicTaskStatus.COMPLETED) return List.of();
        if (!hasOperationalHeart(state)) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        InfectionCell target = task.infectionTarget().orElseThrow();
        if (!state.bootstrap().bounds().contains(target.originAtY(64))) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        FixedRatio prior = state.infection().getOrDefault(target, new FixedRatio(FixedScalar.ZERO));
        long raw = Math.min(FixedScalar.SCALE, Math.addExact(prior.value().raw(), PULSE_GAIN));
        List<ProposedEvent> events = new java.util.ArrayList<>();
        if (task.status() == StrategicTaskStatus.PENDING) events.add(transition(task, StrategicTaskStatus.ACTIVE));
        events.add(new ProposedEvent(task.ownerId(), new InfectionChanged(target, new FixedRatio(new FixedScalar(raw)))));
        if (raw == FixedScalar.SCALE) {
            events.add(transition(task, StrategicTaskStatus.COMPLETED));
        } else {
            events.add(new ProposedEvent(task.ownerId(), new ScheduleEffect.Created(task(task, pulse(action) + 1, action.dueAt().ticks() + COLD_PULSE_INTERVAL))));
        }
        return List.copyOf(events);
    }

    static Optional<InfectionCell> expansionTarget(FrontierWorldState state, long now) {
        if (!hasOperationalHeart(state)) return Optional.empty();
        Map<InfectionCell, FixedRatio> perceived = state.strategicPlans().hiveTerritoryKnowledge().freshInfection(now);
        if (perceived.isEmpty()) return roots(state).stream().map(organ -> InfectionCell.at(organ.anchor())).findFirst();
        FrontierInfectionFrontier frontier = FrontierWorldStateSupport.infectionFrontier(perceived, state.bootstrap().bounds());
        return state.strategicPlans().hiveSettlementKnowledge().freshest(now).flatMap(sighting -> frontier.bestToward(perceived, sighting.settlementAnchor()))
                .or(() -> frontier.best(perceived));
    }

    static boolean hasOperationalHeart(FrontierWorldState state) { return !roots(state).isEmpty(); }

    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) {
        return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status));
    }

    private static int pulse(ScheduledAction action) { return FrontierWorldScheduleSupport.ordinal(action.id().value()); }

    private static List<HiveOrgan> roots(FrontierWorldState state) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(organ -> organ.kind() == HiveOrganKind.HEART && state.isHiveOrganOperational(organ.id()))
                .sorted(Comparator.comparing(HiveOrgan::id)).toList();
    }
}
