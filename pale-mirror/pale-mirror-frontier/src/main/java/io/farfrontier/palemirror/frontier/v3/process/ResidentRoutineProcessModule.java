package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;
import java.util.*;

/** Optional activities have one registered causal owner, not a composition-root tick switch. */
final class ResidentRoutineProcessModule implements FrontierWorldProcessModule {
  static final Set<String> TYPES =
      Set.of(
          RoutineEvent.Requested.TYPE,
          RoutineEvent.Started.TYPE,
          RoutineEvent.Arrived.TYPE,
          RoutineEvent.Ended.TYPE);
  static final DeterministicProcessDescriptor DESCRIPTOR =
      new DeterministicProcessDescriptor(
          "resident-routines",
          Set.of(RoutineEvent.Requested.TYPE),
          Set.of(ResidentRoutineProcess.PROGRESS),
          TYPES,
          ProcessTypeSets.union(
              TYPES,
              Set.of(
                  "frontier.actor_movement_started",
                  "frontier.actor_movement_interrupted",
                  "kernel.schedule_created",
                  "kernel.schedule_rescheduled",
                  "kernel.schedule_cancelled",
                  "kernel.schedule_consumed")),
          TYPES);

  public CommandPlan planCommand(FrontierWorldState s, FrontierCommand command) {
    if (!(command.payload() instanceof RoutineEvent.Requested requested))
      return FrontierWorldCommandPlanner.rejected("routine owner admits only a current request");
    try {
      var row = ResidentRoutines.requested(s, requested, command.submittedAt().ticks());
      var events = new ArrayList<ProposedEvent>();
      events.add(new ProposedEvent(row.actor(), new RoutineEvent.Started(row)));
      if (row.phase() == ResidentRoutine.Phase.WALKING)
        events.addAll(ActorMovementProcess.planStart(RoutineMovementProvider.movement(row)));
      long due =
          row.phase() == ResidentRoutine.Phase.WALKING
              ? row.startedAt()
                  + s.bootstrap().ruleset().residentLife().routines().walkingBudgetTicks()
              : row.phaseAt() + row.pauseTicks();
      events.add(
          new ProposedEvent(
              row.actor(), new ScheduleEffect.Created(ResidentRoutineProcess.progress(row, due))));
      return new CommandPlan.Accepted(List.copyOf(events));
    } catch (IllegalArgumentException invalid) {
      return FrontierWorldCommandPlanner.rejected(invalid.getMessage());
    }
  }

  public FrontierWorldState reduce(FrontierWorldState s, FrontierEvent event) {
    return switch (event.payload()) {
      case RoutineEvent.Started e ->
          ResidentRoutines.started(s, e.routine(), event.instant().ticks());
      case RoutineEvent.Arrived e ->
          ResidentActivityProcess.retargetHotResident(
              ResidentRoutines.arrived(s, e, event.instant().ticks()),
              event.subject(),
              event.instant().ticks());
      case RoutineEvent.Ended e ->
          ResidentActivityProcess.retargetHotResident(
              ResidentRoutines.ended(s, e, event.instant().ticks()),
              event.subject(),
              event.instant().ticks());
      default -> throw new IllegalArgumentException("foreign routine event");
    };
  }
}
