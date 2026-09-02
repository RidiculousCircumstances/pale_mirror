package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Optional;

/**
 * One bounded exact owner that can use the shared engineering COLD/HOT work-site protocol.
 *
 * <p>The interface is deliberately narrow: it owns neither a route policy nor a Minecraft
 * effect.  A bypass construction and an in-place maintenance operation remain distinct
 * canonical aggregates; sharing their crew/assembly shape must not turn either into the other.</p>
 */
public sealed interface EngineeringWorkOrder permits RouteConstruction, RouteMaintenance {
    SubjectId id();
    SubjectId settlementId();
    List<BlockPosition> workCells();
    int confirmedCells();
    Optional<SubjectId> cargoId();
    Optional<EngineeringRecoveryTeam> engineeringTeam();
    Optional<EngineeringWorkAssembly> assembly();

    boolean building();
    boolean readyForToolReturn();
}
