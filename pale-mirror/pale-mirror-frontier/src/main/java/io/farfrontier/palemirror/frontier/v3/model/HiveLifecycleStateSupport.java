package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Pure cross-component invariant for exact bioform lifecycle custody.
 *
 * <p>{@link HiveColony} owns lifecycle and cocoon reservations, {@link ActorLocation}
 * owns canonical supported position, and common execution owns activity authority.
 * Ambient leases retain presentation, not body authority. This support verifies their boundary without making the aggregate state
 * record another hive-physiology owner.</p>
 */
final class HiveLifecycleStateSupport {
    private HiveLifecycleStateSupport() { }

    static void validateCocoonCustody(FrontierBootstrap bootstrap, HiveColony colony,
                                      Map<SubjectId, ActorLocation> actorLocations,
                                      Map<SubjectId, AmbientActorLease> ambientLeases,
                                      Map<BlockPosition, PhysicalDelta> physicalDeltas,
                                      io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState executions) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        Objects.requireNonNull(colony, "hive colony");
        Objects.requireNonNull(actorLocations, "actor locations");
        Objects.requireNonNull(ambientLeases, "ambient leases");
        Objects.requireNonNull(physicalDeltas, "physical deltas");
        Objects.requireNonNull(executions, "actor executions");

        executions.current(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE)
                .keySet().forEach(actorId -> {
            ActorLocation actor = actorLocations.get(actorId);
            if (actor != null && actor.kind() == ActorKind.BIOFORM
                    && !HivePresencePolicy.permits(colony, physicalDeltas, actorId))
                throw new IllegalArgumentException("passive bioform presence contradicts its physiology or mobilisation");
        });

        colony.bioformLifecycles().forEach((actorId, lifecycle) -> {
            if (!lifecycle.phase().occupiesCocoon()) return;
            HiveCocoonSlot slot = lifecycle.homeSlot().orElseThrow();
            HiveOrgan hibernaculum = hibernaculum(bootstrap, colony, slot);
            ActorLocation actor = actorLocations.get(actorId);
            BodyPosition expected = BodyPosition.above(new SurfaceAnchor(HiveCocoonPlan.cocoonCell(hibernaculum, slot)));
            if (actor == null || !actor.body().equals(expected)) {
                throw new IllegalArgumentException("cocoon-retained bioform must remain at its exact cocoon body position");
            }
        });

        ambientLeases.values().stream().filter(lease -> lease.status() != AmbientLeaseStatus.CLOSED).forEach(lease -> {
            BioformLifecycle lifecycle = colony.bioformLifecycles().get(lease.actorId());
            if (lifecycle != null && !HivePhysiologySupport.permitsAmbientLease(colony, physicalDeltas, lease.actorId())) {
                throw new IllegalArgumentException("cocoon-retained or unconfirmed waking bioform may not retain an active ambient lease");
            }
        });
    }

    private static HiveOrgan hibernaculum(FrontierBootstrap bootstrap, HiveColony colony, HiveCocoonSlot slot) {
        return Stream.concat(bootstrap.hive().organs().stream(), colony.addedOrgans().values().stream())
                .filter(candidate -> candidate.id().equals(slot.hibernaculumId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("cocoon lifecycle references an absent HIBERNACULUM"));
    }
}
