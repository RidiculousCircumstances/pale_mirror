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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Executes one durable hive infection-expansion task, never an ownerless metabolism pulse. */
final class HiveInfectionProcess {
    private static final long PULSE_INTERVAL = 100L;
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
            throw new IllegalStateException("hive infection schedule has no owned expansion task");
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
            events.add(new ProposedEvent(task.ownerId(), new ScheduleEffect.Created(task(task, pulse(action) + 1, action.dueAt().ticks() + PULSE_INTERVAL))));
        }
        return List.copyOf(events);
    }

    static Optional<InfectionCell> expansionTarget(FrontierWorldState state) {
        if (!hasOperationalHeart(state)) return Optional.empty();
        if (state.infection().isEmpty()) return roots(state).stream().map(organ -> InfectionCell.at(organ.anchor())).findFirst();
        LinkedHashSet<InfectionCell> candidates = new LinkedHashSet<>();
        state.infection().keySet().stream().sorted(Comparator.comparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z))
                .forEach(source -> adjacent(source).stream().filter(cell -> state.bootstrap().bounds().contains(cell.originAtY(64))).forEach(candidates::add));
        return candidates.stream().sorted(Comparator.comparingLong((InfectionCell cell) -> state.infection()
                .getOrDefault(cell, new FixedRatio(FixedScalar.ZERO)).value().raw()).thenComparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z)).findFirst();
    }

    static boolean hasOperationalHeart(FrontierWorldState state) { return !roots(state).isEmpty(); }

    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) {
        return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status));
    }

    private static int pulse(ScheduledAction action) { return FrontierWorldScheduleSupport.ordinal(action.id().value()); }

    private static List<InfectionCell> adjacent(InfectionCell source) {
        return List.of(new InfectionCell(source.x() + 1, source.z()), new InfectionCell(source.x(), source.z() + 1),
                new InfectionCell(source.x() - 1, source.z()), new InfectionCell(source.x(), source.z() - 1));
    }

    private static List<HiveOrgan> roots(FrontierWorldState state) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(organ -> organ.kind() == HiveOrganKind.HEART && state.isHiveOrganOperational(organ.id()))
                .sorted(Comparator.comparing(HiveOrgan::id)).toList();
    }
}
