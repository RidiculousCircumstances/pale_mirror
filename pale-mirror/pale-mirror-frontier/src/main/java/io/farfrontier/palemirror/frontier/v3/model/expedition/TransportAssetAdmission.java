package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** Fleet capability admission, not a body lifecycle or another stock reservation register. */
public final class TransportAssetAdmission {
    private record Query(TransportAsset asset, SubjectId home) { }
    private static final ImmutableSnapshotView<Query, Boolean> AVAILABILITY = new ImmutableSnapshotView<>(4, 256);
    private TransportAssetAdmission() { }
    public static boolean available(FrontierWorldState state, TransportAsset asset, SubjectId home) {
        // Every hypothetical load/duration asks the same finite fleet question.
        // Retain only a completed answer for this exact immutable world image;
        // departure still checks its current body, custody and mission owners.
        return AVAILABILITY.get(state, new Query(asset, home), () -> current(state, asset, home));
    }
    private static boolean current(FrontierWorldState state, TransportAsset asset, SubjectId home) {
        return asset.equals(state.transportFleet().assets().get(asset.actorId())) && asset.homeSettlementId().equals(home)
                && asset.missionId().isEmpty() && !ReferenceContainerCustody.blocksCanonicalUse(state, asset.containerId())
                && ActorInventoryInteractionFences.pendingOwner(state, asset.actorId()).isEmpty()
                && ActorExecutionCoordinator.declaredActorWorkAdmission(state, asset.actorId()).permitted();
    }
    public static void requireCourier(FrontierWorldState state, Shipment shipment, SubjectId home) {
        var asset = state.transportFleet().require(shipment.execution().actorId());
        if (!shipment.mobileContainerId().equals(Optional.of(asset.containerId())) || shipment.transportMissionId().isEmpty()
                || !available(state, asset, home))
            throw new IllegalArgumentException("mobile shipment lacks an available exact finite transport capability");
    }
}
