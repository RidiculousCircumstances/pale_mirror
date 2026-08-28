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

/** Bounded infection metabolism rooted only in operational hive HEART organs. */
final class HiveInfectionProcess {
    private static final SubjectId HIVE = new SubjectId("hive:frontier");
    private static final long PULSE_INTERVAL = 100L;
    private static final long PULSE_GAIN = 125_000L;

    private HiveInfectionProcess() { }

    static ScheduledAction pulse(int ordinal, long due) {
        return new ScheduledAction(new ScheduleId("schedule:infection-pulse-" + ordinal), new SimInstant(due), 0,
                HIVE, "frontier.infection.pulse", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        if (!HIVE.equals(action.subject())) throw new IllegalStateException("infection pulse has a foreign hive subject");
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        ProposedEvent next = new ProposedEvent(HIVE, new ScheduleEffect.Created(pulse(ordinal + 1, action.dueAt().ticks() + PULSE_INTERVAL)));
        List<HiveOrgan> roots = roots(state);
        if (roots.isEmpty()) return List.of(next);
        List<InfectionCell> cells = state.infection().keySet().stream().sorted(Comparator.comparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z)).toList();
        InfectionCell source = cells.isEmpty() ? InfectionCell.at(roots.get(Math.floorMod(ordinal - 1, roots.size())).anchor())
                : cells.get(Math.floorMod(ordinal - 1, cells.size()));
        InfectionCell target = cells.isEmpty() ? source : adjacent(source, ordinal);
        if (!state.bootstrap().bounds().contains(target.originAtY(64))) target = source;
        FixedRatio prior = state.infection().getOrDefault(target, new FixedRatio(FixedScalar.ZERO));
        long raw = Math.min(FixedScalar.SCALE, Math.addExact(prior.value().raw(), PULSE_GAIN));
        return List.of(new ProposedEvent(HIVE, new InfectionChanged(target, new FixedRatio(new FixedScalar(raw)))), next);
    }

    private static InfectionCell adjacent(InfectionCell source, int ordinal) {
        return switch (Math.floorMod(ordinal - 1, 4)) {
            case 0 -> new InfectionCell(source.x() + 1, source.z());
            case 1 -> new InfectionCell(source.x(), source.z() + 1);
            case 2 -> new InfectionCell(source.x() - 1, source.z());
            default -> new InfectionCell(source.x(), source.z() - 1);
        };
    }

    private static List<HiveOrgan> roots(FrontierWorldState state) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream())
                .filter(organ -> organ.kind() == HiveOrganKind.HEART && state.isHiveOrganOperational(organ.id()))
                .sorted(Comparator.comparing(HiveOrgan::id)).toList();
    }
}
