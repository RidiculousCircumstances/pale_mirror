package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Current-format captured authority shared by activity receipts, never allocated by decoding. */
final class ActorHotObservationCodec {
    private ActorHotObservationCodec() { }
    static void write(DataOutputStream out, ActorHotObservation value) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(out, value.actuation().body().actorId());
        out.writeLong(value.actuation().body().physicalEpoch());
        ActorExecutionStateCodec.writeId(out, value.actuation().execution());
        out.writeLong(value.scopeRevision());
    }
    static ActorHotObservation read(DataInputStream in) throws IOException {
        var body = new ActorBodyId(FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong());
        return new ActorHotObservation(new ActorActuationId(body, ActorExecutionStateCodec.readId(in)), in.readLong());
    }
}
