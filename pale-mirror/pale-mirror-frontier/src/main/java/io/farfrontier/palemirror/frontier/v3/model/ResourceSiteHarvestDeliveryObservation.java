package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Observed farmer hand and depot layout. actorEpoch fences resource custody, not scene/body versions. */
public record ResourceSiteHarvestDeliveryObservation(PhysicalObservationId id, PhysicalIntentId intentId,
                                                     SubjectId siteId, SubjectId jobId, SubjectId workerId,
                                                     SubjectId actorAccountId, SubjectId depotAccountId,
                                                     SceneLeaseId leaseId, UUID entityId, long actorEpoch,
                                                     int harvestedQuantity, long depotEpoch,
                                                     List<FungiblePhysicalObservation.Stack> depotStacks,
                                                     String depotFingerprint, long emittedCanonicalRevision,
                                                     String witnessId) implements PhysicalEffectObservation {
    public ResourceSiteHarvestDeliveryObservation {
        Objects.requireNonNull(id, "field delivery observation id");
        Objects.requireNonNull(intentId, "field delivery intent");
        Objects.requireNonNull(siteId, "field delivery site");
        Objects.requireNonNull(jobId, "field delivery job");
        Objects.requireNonNull(workerId, "field delivery farmer");
        Objects.requireNonNull(actorAccountId, "field delivery actor account");
        Objects.requireNonNull(depotAccountId, "field delivery depot account");
        Objects.requireNonNull(leaseId, "field delivery scene");
        Objects.requireNonNull(entityId, "field delivery body");
        depotStacks = List.copyOf(Objects.requireNonNull(depotStacks, "field delivery chest observation"));
        depotFingerprint = Objects.requireNonNull(depotFingerprint, "field delivery chest fingerprint");
        witnessId = Objects.requireNonNull(witnessId, "field delivery physical witness");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !workerId.value().startsWith("resident:")
                || !actorAccountId.value().startsWith("custody:field-actor-")
                || !depotAccountId.value().startsWith("custody:")
                || actorEpoch < 1 || harvestedQuantity < 0 || harvestedQuantity > 64
                || depotStacks.size() > 128 || depotStacks.stream().anyMatch(Objects::isNull)
                || depotStacks.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != depotStacks.size()
                || witnessId.isBlank() || witnessId.length() > 256 || emittedCanonicalRevision < 0
                || harvestedQuantity == 0 && (depotEpoch != 0 || !depotStacks.isEmpty()
                        || !depotFingerprint.equals("not_applicable"))
                || harvestedQuantity > 0 && (depotEpoch < 1 || depotStacks.isEmpty()
                        || !depotFingerprint.matches("sha256:[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("field delivery observation is incomplete or inconsistent");
        }
    }
}
