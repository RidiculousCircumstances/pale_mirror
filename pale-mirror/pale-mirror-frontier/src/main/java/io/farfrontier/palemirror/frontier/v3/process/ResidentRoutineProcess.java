package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;

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
    long cadence = s.bootstrap().ruleset().residentLife().routines().admissionInterval();
    // A concurrent native healing/interaction receipt retains this exact execution.
    // Its physical owner must settle before any routine pose or terminal transition.
    if (ActorInventoryInteractionFences.pending(s, row.actor()))
      return List.of(wake(row, now + cadence));
    if (row.kind() == ResidentRoutine.Kind.SIT) {
      var use = ResidentRoutines.use(s, row);
      if (use.pendingBody().isPresent()) return List.of(wake(row, now + cadence));
      if (row.phase() == ResidentRoutine.Phase.SEATING && use.phase() == SeatUse.Phase.SEATED
          && s.actorLocations().get(row.actor()).supportingSurface().equals(use.place().contact()))
        return List.of(new ProposedEvent(row.actor(), new RoutineEvent.Seated(row.execution().orElseThrow(), row.sequence())), wake(row, now + cadence));
      boolean done = ResidentRoutines.finishing(s, row, now);
      if (ActorExecutionCoordinator.coldAvailable(s, row.actor())) {
        if (!done && row.phase() == ResidentRoutine.Phase.SEATING && use.phase() == SeatUse.Phase.RESERVED
            && s.actorLocations().get(row.actor()).supportingSurface().equals(use.place().approach())
            && SeatContacts.furnitureKnowledge(s, use.place()) == SeatContacts.FurnitureKnowledge.PRESENT && SeatContacts.approachClear(s, use))
          return List.of(new ProposedEvent(row.actor(), new SeatEvent.Changed(row.actor(), use.revision(), Optional.empty(), SeatUse.Action.ENTER,
              SeatUse.ExecutionMode.LOCAL, true, use.place().contact().standingBody())), wake(row, now + cadence));
        if (done && use.phase() == SeatUse.Phase.SEATED) {
          var exit = SeatContacts.coldExit(s, use);
          if (exit.isPresent()) return List.of(new ProposedEvent(row.actor(), new SeatEvent.Changed(row.actor(), use.revision(), Optional.empty(), SeatUse.Action.EXIT,
              SeatUse.ExecutionMode.LOCAL, true, exit.orElseThrow().standingBody())), wake(row, now + cadence));
        }
      }
      if (!done || use.phase() == SeatUse.Phase.SEATED) return List.of(wake(row, now + cadence));
    } else if (!ResidentRoutines.finishing(s, row, now)) {
      long end = row.phase() == ResidentRoutine.Phase.PAUSING ? row.phaseAt() + row.pauseTicks()
          : row.startedAt() + s.bootstrap().ruleset().residentLife().routines().walkingBudgetTicks();
      return List.of(wake(row, end));
    }
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
