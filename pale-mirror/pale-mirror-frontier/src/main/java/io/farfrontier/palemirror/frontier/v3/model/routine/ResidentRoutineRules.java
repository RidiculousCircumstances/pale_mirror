package io.farfrontier.palemirror.frontier.v3.model.routine;

/** Persisted hashed policy. Memory/candidate safety ceilings remain in their algorithms. */
public record ResidentRoutineRules(
    boolean enabled,
    int admissionInterval,
    int candidateVisits,
    int activeLimit,
    long cooldownTicks,
    long pauseTicks,
    long walkingBudgetTicks,
    int strollRadius,
    int eatingTicks) {
  public ResidentRoutineRules {
    if (admissionInterval < 20
        || candidateVisits < 1
        || candidateVisits > 32
        || activeLimit < 1
        || activeLimit > 32
        || pauseTicks < 20
        || pauseTicks > 1200
        || walkingBudgetTicks < 100
        || walkingBudgetTicks > 2400
        || cooldownTicks <= pauseTicks + walkingBudgetTicks
        || strollRadius < 2
        || strollRadius > 32
        || eatingTicks < 20
        || eatingTicks > 1200)
      throw new IllegalArgumentException("invalid resident routine policy");
  }

  public static ResidentRoutineRules disabled() {
    return new ResidentRoutineRules(false, 20, 16, 12, 2400, 160, 600, 20, 160);
  }

  public static ResidentRoutineRules initial() {
    var r = disabled();
    return new ResidentRoutineRules(
        true,
        r.admissionInterval,
        r.candidateVisits,
        r.activeLimit,
        r.cooldownTicks,
        r.pauseTicks,
        r.walkingBudgetTicks,
        r.strollRadius,
        r.eatingTicks);
  }

  public String canonicalText() {
    return enabled
        + ":"
        + admissionInterval
        + ":"
        + candidateVisits
        + ":"
        + activeLimit
        + ":"
        + cooldownTicks
        + ":"
        + pauseTicks
        + ":"
        + walkingBudgetTicks
        + ":"
        + strollRadius
        + ":"
        + eatingTicks;
  }
}
