package io.farfrontier.palemirror.frontier;

/**
 * Public settlement regime chosen from canonical risk and reserves.  It is intentionally
 * independent of a facility operation: a damaged workshop is one fact contributing to policy,
 * not a hidden substitute for the settlement's current civilian order.
 */
public enum FrontierCivicState {
    NORMAL,
    WATCH,
    EMERGENCY,
    SIEGE,
    RECOVERY,
    DECLINED
}
