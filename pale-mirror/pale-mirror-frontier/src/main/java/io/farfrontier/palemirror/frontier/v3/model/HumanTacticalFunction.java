package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Current player-visible combat function, derived from exact human state.
 *
 * <p>This is deliberately not a resident class, an inventory or a second unit
 * roster.  Its inputs remain the owning assignment, defender-unit leadership,
 * profession and exact actor-held items.</p>
 */
public enum HumanTacticalFunction {
    CIVILIAN,
    MILITIA,
    ARMED_DEFENDER,
    GUARD,
    SQUAD_LEADER
}
