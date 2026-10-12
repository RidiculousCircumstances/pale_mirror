package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Shared exact semantic-goal encoding; callers retain the same declaration without route discovery. */
final class MovementOrderCodec {
    private MovementOrderCodec() { }
    static void write(DataOutputStream out, MovementOrder order) throws IOException {
        FrontierWorldStateCodec.writeString(out, order.ownerId().value());
        FrontierWorldStateCodec.writeString(out, order.actorId().value());
        out.writeLong(order.goalOrdinal()); out.writeLong(order.goalRevision());
        out.writeByte(FrontierWireTags.tag(order.capability())); out.writeByte(FrontierWireTags.tag(order.arrivalPolicy()));
        FrontierWorldStateCodec.writeCount(out, order.legalStations().size());
        for (var station : order.legalStations()) FrontierWorldStateCodec.writePosition(out, station.support());
    }
    static MovementOrder read(DataInputStream in) throws IOException {
        var owner = new SubjectId(FrontierWorldStateCodec.readString(in));
        var actor = new SubjectId(FrontierWorldStateCodec.readString(in));
        long ordinal = in.readLong(), revision = in.readLong();
        var capability = FrontierWireTags.require(TraversalCapability.class, in.readUnsignedByte());
        var policy = FrontierWireTags.require(MovementOrder.ArrivalPolicy.class, in.readUnsignedByte());
        int count = FrontierWorldStateCodec.readCount(in);
        if (count < 1 || count > MovementOrder.MAX_LEGAL_STATIONS) throw new IllegalArgumentException("invalid movement goal station count");
        var stations = new java.util.ArrayList<SurfaceAnchor>(count);
        for (int index = 0; index < count; index++) stations.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
        return new MovementOrder(owner, actor, ordinal, revision, stations, capability, policy);
    }
}
