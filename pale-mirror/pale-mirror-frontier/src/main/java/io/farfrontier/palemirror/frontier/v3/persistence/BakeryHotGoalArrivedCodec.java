package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.BakeryHotGoalArrived;
import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkState;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;

final class BakeryHotGoalArrivedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_hot_goal_arrived"; }
    @Override public byte[] encode(FrontierPayload payload) {
        BakeryHotGoalArrived arrived = (BakeryHotGoalArrived) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, arrived.jobId());
            FrontierWorldPayloadCodecs.writeString(output, arrived.leaseId().value());
            output.writeByte(arrived.phase().wireTag());
            output.writeInt(arrived.observedWorker().x());
            output.writeInt(arrived.observedWorker().y());
            output.writeInt(arrived.observedWorker().z());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new BakeryHotGoalArrived(
                FrontierWorldPayloadCodecs.readSubject(input).value(),
                new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)),
                BakeryWorkState.Phase.fromWireTag(input.readUnsignedByte()),
                new BodyPosition(input.readInt(), input.readInt(), input.readInt())));
    }
}
