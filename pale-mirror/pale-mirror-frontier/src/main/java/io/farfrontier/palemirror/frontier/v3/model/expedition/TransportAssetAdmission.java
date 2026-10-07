package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** Fleet capability admission, not a body lifecycle or another stock reservation register. */
public final class TransportAssetAdmission {
    private TransportAssetAdmission() { }
    public static boolean available(FrontierWorldState state, TransportAsset asset, SubjectId home) {
        return asset.equals(state.transportFleet().assets().get(asset.actorId())) && asset.homeSettlementId().equals(home)
                && asset.missionId().isEmpty() && !ReferenceContainerCustody.blocksCanonicalUse(state, asset.containerId())
                && ActorInventoryInteractionFences.pendingOwner(state, asset.actorId()).isEmpty()
                && ActorExecutionCoordinator.declaredActorWorkAdmission(state, asset.actorId()).permitted();
    }
    public static void requireCourier(FrontierWorldState state, Shipment shipment) {
        var asset = state.transportFleet().require(shipment.execution().actorId());
        if (!shipment.mobileContainerId().equals(Optional.of(asset.containerId())) || shipment.transportMissionId().isEmpty()
                || !available(state, asset, shipment.sender().settlementId()))
            throw new IllegalArgumentException("mobile shipment lacks an available exact finite transport capability");
    }
}
