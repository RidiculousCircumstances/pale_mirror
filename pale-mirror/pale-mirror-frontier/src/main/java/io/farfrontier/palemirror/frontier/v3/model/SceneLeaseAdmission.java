package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Marker for a command/event that proposes a new persisted scene lease.
 *
 * <p>The model exposes only the exact lease. The closed process/scene SDK consumes this
 * capability at command admission, event reduction and snapshot recovery, without teaching
 * canonical scene semantics about process families or physical-provider budgets.</p>
 */
public interface SceneLeaseAdmission {
    SceneLease lease();
}
