package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRouteNetwork;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.Settlement;
import io.farfrontier.palemirror.frontier.v3.model.StructureKind;

import java.util.Optional;

/** Exact diagnostic anchors for one ordinary settlement ingress package; never a projection query. */
final class FrontierV3SettlementIngressGeometry {
    record Value(BlockPosition farmAnchor, BlockPosition routeSurface) { }

    private FrontierV3SettlementIngressGeometry() { }

    static Optional<Value> find(FrontierWorldState state, SubjectId settlementId) {
        if (settlementId == null) return Optional.empty();
        Settlement settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst().orElse(null);
        if (settlement == null) return Optional.empty();
        return settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.FARM).findFirst()
                .map(farm -> new Value(farm.anchor(), FrontierRouteNetwork.supplyWaypoints(state.bootstrap(), settlementId).getFirst()));
    }
}
