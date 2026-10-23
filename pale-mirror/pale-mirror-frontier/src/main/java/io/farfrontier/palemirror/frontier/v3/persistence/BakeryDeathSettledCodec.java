package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

final class BakeryDeathSettledCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_death_settled"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (BakeryDeathSettled) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            out.writeUTF(value.identity().body().actorId().value()); out.writeLong(value.identity().body().physicalEpoch());
            ActorExecutionStateCodec.writeId(out, value.identity().execution()); out.writeByte(value.expectedPhase().wireTag());
            out.writeLong(value.sourceEpoch()); GroundDispositionCodec.write(out, value.ground());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
            var body = new ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
            return new BakeryDeathSettled(new ActorActuationId(body, ActorExecutionStateCodec.readId(in)),
                    StationWorkState.Phase.fromWireTag(in.readUnsignedByte()), in.readLong(), GroundDispositionCodec.read(in));
        });
    }
}
