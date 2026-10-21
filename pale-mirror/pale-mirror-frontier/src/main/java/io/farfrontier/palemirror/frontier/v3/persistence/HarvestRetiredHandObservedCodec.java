package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

final class HarvestRetiredHandObservedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.harvest_retired_hand_observed"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (HarvestRetiredHandObserved) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            out.writeUTF(value.identity().body().actorId().value()); out.writeLong(value.identity().body().physicalEpoch());
            ActorExecutionStateCodec.writeId(out, value.identity().execution()); out.writeInt(value.completedCells());
            out.writeUTF(value.siteId().value()); out.writeUTF(value.accountId().value()); out.writeLong(value.resourceEpoch());
            out.writeUTF(((PhysicalStackAddress.ActorHand) value.hand().address()).entityId().toString());
            out.writeUTF(value.hand().itemKind()); out.writeInt(value.hand().quantity());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
            var body = new ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
            var identity = new ActorActuationId(body, ActorExecutionStateCodec.readId(in));
            int completed = in.readInt(); var site = new SubjectId(in.readUTF()); var account = new SubjectId(in.readUTF());
            long epoch = in.readLong(); var entity = java.util.UUID.fromString(in.readUTF());
            return new HarvestRetiredHandObserved(identity, site, account, epoch, new FungiblePhysicalObservation.Stack(
                    new PhysicalStackAddress.ActorHand(body.actorId(), entity), in.readUTF(), in.readInt()), completed);
        });
    }
}
