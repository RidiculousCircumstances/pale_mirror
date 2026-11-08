package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.routine.*;

/**
 * Read-only versioned story of an exact optional activity, including cancellation/terminal closure.
 */
public record ResidentRoutineProgressObligation(
    long generation, String fingerprint, long budgetTicks, String disposition, String next) {
  public static ResidentRoutineProgressObligation describe(FrontierWorldState s, SubjectId actor) {
    var row = s.routines().rows().get(actor);
    var rules = s.bootstrap().ruleset().residentLife().routines();
    if (row == null)
      return new ResidentRoutineProgressObligation(
          1, "CLOSED", rules.walkingBudgetTicks(), "TERMINAL", "CLOSED");
    if (row.phase() == ResidentRoutine.Phase.CLOSED)
      return new ResidentRoutineProgressObligation(
          Math.addExact(row.sequence(),1L),
          row.sequence() + ":CLOSED",
          rules.walkingBudgetTicks(),
          "TERMINAL",
          "CLOSED");
    var lease = s.ambientLeases().get(actor);
    String disposition =
        lease != null
                && lease.status() != AmbientLeaseStatus.HOT
                && lease.status() != AmbientLeaseStatus.CLOSED
            ? "RECOVERY_UNKNOWN"
            : "ELIGIBLE";
    String fingerprint =
        row.sequence()
            + ":"
            + row.kind()
            + ":"
            + row.phase()
            + ":"
            + row.phaseAt()
            + ":"
            + s.actorLocations().get(actor).body();
    return new ResidentRoutineProgressObligation(
        Math.addExact(row.sequence(),1L),
        fingerprint,
        row.phase() == ResidentRoutine.Phase.WALKING
            ? rules.walkingBudgetTicks() + rules.admissionInterval()
            : row.pauseTicks() + rules.admissionInterval(),
        disposition,
        row.phase().name());
  }
}
