package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

final class HarvestDeathSettledCodec implements PayloadCodec {
    @Override public String type() { return "frontier.harvest_death_settled"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (HarvestDeathSettled) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            out.writeUTF(value.siteId().value()); out.writeUTF(value.identity().body().actorId().value());
            out.writeLong(value.identity().body().physicalEpoch()); ActorExecutionStateCodec.writeId(out, value.identity().execution());
            out.writeInt(value.completedCells()); out.writeInt(value.harvestedQuantity()); out.writeInt(value.deliveredQuantity());
            out.writeLong(value.sourceEpoch()); GroundDispositionCodec.write(out, value.ground());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
            var site = new SubjectId(in.readUTF()); var body = new ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
            return new HarvestDeathSettled(site, new ActorActuationId(body, ActorExecutionStateCodec.readId(in)),
                    in.readInt(), in.readInt(), in.readInt(), in.readLong(), GroundDispositionCodec.read(in));
        });
    }
}
