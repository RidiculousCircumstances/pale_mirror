package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import java.util.*;

/** Only active purposes have a timer; closed per-person cooldowns have none. */
public final class ResidentRoutineProcess {
  public static final String PROGRESS = "frontier.resident.routine.progress";

  public static ScheduledAction progress(ResidentRoutine row, long tick) {
    return new ScheduledAction(
        new ScheduleId(
            "schedule:resident-routine-"
                + row.actor().value().replace(':', '-')
                + "-"
                + row.sequence()),
        new SimInstant(tick),
        14,
        row.actor(),
        PROGRESS,
        1);
  }

  public static ProposedEvent wake(ResidentRoutine row, long tick) {
    var due = progress(row, tick);
    return new ProposedEvent(row.actor(), new ScheduleEffect.Rescheduled(due.id(), due));
  }

  public static List<ProposedEvent> plan(FrontierWorldState s, ScheduledAction action, long tick) {
    var row = s.routines().rows().get(action.subject());
    if (row == null
        || row.phase() == ResidentRoutine.Phase.CLOSED
        || !action.id().equals(progress(row, action.dueAt().ticks()).id()))
      return List.of(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
    long now = Math.max(tick, action.dueAt().ticks());
    long end =
        row.phase() == ResidentRoutine.Phase.PAUSING
            ? row.phaseAt() + row.pauseTicks()
            : row.startedAt()
                + s.bootstrap().ruleset().residentLife().routines().walkingBudgetTicks();
    if (now < end) return List.of(wake(row, end));
    var exact = row.execution().orElseThrow();
    var checkpoint = ActorMovementProcess.ownerTransitionCheckpoint(s, exact, now);
    if (!(checkpoint instanceof ActivityInterruptionPlanner.Ready ready))
      return List.of(
          wake(row, now + s.bootstrap().ruleset().residentLife().routines().admissionInterval()));
    var events = new ArrayList<ProposedEvent>(ready.events());
    events.add(new ProposedEvent(row.actor(), new RoutineEvent.Ended(exact, row.sequence())));
    events.add(new ProposedEvent(row.actor(), new ScheduleEffect.Consumed(action.id())));
    events.add(ResidentActivityProcess.wakeAfterActivity(row.actor(), now));
    return List.copyOf(events);
  }

  private ResidentRoutineProcess() {}
}
