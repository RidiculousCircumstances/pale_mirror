package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;

final class BakeryStationSceneReconciledCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_station_scene_reconciled"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (BakeryStationSceneReconciled) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            FrontierWorldPayloadCodecs.writeSubject(out, value.jobId());
            FrontierWorldPayloadCodecs.writeString(out, value.leaseId().value());
            out.writeLong(value.leaseRevision()); out.writeLong(value.recoveryEpoch());
            FrontierWorldPayloadCodecs.writeString(out, value.entityId().toString());
            out.writeInt(value.observedBody().x()); out.writeInt(value.observedBody().y()); out.writeInt(value.observedBody().z());
            out.writeInt(value.phase().wireTag());
            out.writeInt(value.source().wireTag());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new BakeryStationSceneReconciled(
                FrontierWorldPayloadCodecs.readSubject(in).value(), new SceneLeaseId(FrontierWorldPayloadCodecs.readString(in)),
                in.readLong(), in.readLong(), java.util.UUID.fromString(FrontierWorldPayloadCodecs.readString(in)),
                new BodyPosition(in.readInt(), in.readInt(), in.readInt()), BakeryWorkState.Phase.fromWireTag(in.readInt()),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.fromWireTag(in.readInt())));
    }
}
