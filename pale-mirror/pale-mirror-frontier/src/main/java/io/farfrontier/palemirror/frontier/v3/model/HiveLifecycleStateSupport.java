package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Pure cross-component invariant for exact bioform lifecycle custody.
 *
 * <p>{@link HiveColony} owns lifecycle and cocoon reservations, {@link ActorLocation}
 * owns the one canonical body, and {@link AmbientActorLease} owns the one HOT body
 * authority. This support verifies their boundary without making the aggregate state
 * record another hive-physiology owner.</p>
 */
final class HiveLifecycleStateSupport {
    private HiveLifecycleStateSupport() { }

    static void validateCocoonCustody(FrontierBootstrap bootstrap, HiveColony colony,
                                      Map<SubjectId, ActorLocation> actorLocations,
                                      Map<SubjectId, AmbientActorLease> ambientLeases,
                                      Map<BlockPosition, PhysicalDelta> physicalDeltas) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        Objects.requireNonNull(colony, "hive colony");
        Objects.requireNonNull(actorLocations, "actor locations");
        Objects.requireNonNull(ambientLeases, "ambient leases");
        Objects.requireNonNull(physicalDeltas, "physical deltas");

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
            if (lifecycle != null && !permitsAmbientLease(colony, physicalDeltas, lease.actorId())) {
                throw new IllegalArgumentException("cocoon-retained or unconfirmed waking bioform may not retain an active ambient lease");
            }
        });
    }

    private static boolean permitsAmbientLease(HiveColony colony, Map<BlockPosition, PhysicalDelta> physicalDeltas,
                                               SubjectId actorId) {
        BioformLifecycle lifecycle = colony.bioformLifecycles().get(actorId);
        if (lifecycle == null) return true;
        if (lifecycle.phase() == BioformLifecyclePhase.ASSEMBLING) {
            return colony.mobilizations().values().stream().anyMatch(mobilization ->
                    (mobilization.status() == HiveMobilizationStatus.ASSEMBLING
                            || mobilization.status() == HiveMobilizationStatus.CONFLICT)
                            && mobilization.releasedMemberIds().contains(actorId));
        }
        if (lifecycle.phase() != BioformLifecyclePhase.WAKING) return lifecycle.phase().permitsAmbientBody();
        return physicalDeltas.values().stream().anyMatch(delta -> delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS
                && delta.ownerId().filter(actorId::equals).isPresent()
                && delta.semanticPart().filter(GrayboxSemanticPart.COCOON::equals).isPresent());
    }

    private static HiveOrgan hibernaculum(FrontierBootstrap bootstrap, HiveColony colony, HiveCocoonSlot slot) {
        return Stream.concat(bootstrap.hive().organs().stream(), colony.addedOrgans().values().stream())
                .filter(candidate -> candidate.id().equals(slot.hibernaculumId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("cocoon lifecycle references an absent HIBERNACULUM"));
    }
}
