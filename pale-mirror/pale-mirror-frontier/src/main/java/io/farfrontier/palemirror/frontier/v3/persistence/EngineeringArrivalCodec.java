package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.EngineeringHotArrival;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/** Captured physical authority, shared by both engineering journey event families. */
final class EngineeringArrivalCodec {
    private EngineeringArrivalCodec() { }
    static void write(DataOutputStream out, Optional<EngineeringHotArrival> arrival) throws IOException {
        out.writeBoolean(arrival.isPresent());
        if (arrival.isEmpty()) return;
        var hot = arrival.orElseThrow();
        FrontierWorldStateCodec.writeString(out, hot.actuation().body().actorId().value());
        out.writeLong(hot.actuation().body().physicalEpoch());
        ActorExecutionStateCodec.writeId(out, hot.actuation().execution());
        out.writeLong(hot.leaseRevision());
    }
    static Optional<EngineeringHotArrival> read(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return Optional.empty();
        var body = new ActorBodyId(new io.farfrontier.palemirror.frontier.v3.api.SubjectId(
                FrontierWorldStateCodec.readString(in)), in.readLong());
        return Optional.of(new EngineeringHotArrival(new ActorActuationId(body, ActorExecutionStateCodec.readId(in)), in.readLong()));
    }
    static void requireAbsent(Optional<EngineeringHotArrival> arrival) {
        if (arrival.isPresent()) throw new IllegalArgumentException("engineering journey admission cannot claim physical arrival");
    }
}
