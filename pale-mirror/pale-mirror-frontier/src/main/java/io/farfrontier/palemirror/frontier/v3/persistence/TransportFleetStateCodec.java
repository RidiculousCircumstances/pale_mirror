package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.expedition.TransportAsset;
import io.farfrontier.palemirror.frontier.v3.model.expedition.TransportFleet;
import java.io.*;
import java.util.*;

/** Bounded exact fleet; recovery never replays bootstrap grants. */
final class TransportFleetStateCodec {
    private TransportFleetStateCodec() { }
    static void write(DataOutputStream out, TransportFleet fleet) throws IOException {
        out.writeInt(fleet.assets().size());
        for (var asset : fleet.assets().values().stream().sorted(Comparator.comparing(TransportAsset::actorId)).toList()) {
            out.writeUTF(asset.actorId().value()); out.writeUTF(asset.homeSettlementId().value());
            switch (asset.kind()) { case CHEST_DONKEY -> out.writeByte(1); }
            out.writeUTF(asset.containerId().value()); out.writeByte(asset.stackSlots());
            FrontierWorldStateCodec.writePosition(out, asset.homeStation().support());
            out.writeBoolean(asset.missionId().isPresent());
            if (asset.missionId().isPresent()) out.writeUTF(asset.missionId().orElseThrow().value());
        }
    }
    static TransportFleet read(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > TransportFleet.MAX_ASSETS) throw new IllegalArgumentException("invalid fleet count");
        var assets = new LinkedHashMap<SubjectId, TransportAsset>();
        for (int i = 0; i < count; i++) {
            var actor = new SubjectId(in.readUTF()); var home = new SubjectId(in.readUTF());
            var kind = switch (in.readUnsignedByte()) { case 1 -> TransportAsset.Kind.CHEST_DONKEY;
                default -> throw new IllegalArgumentException("unknown transport asset kind"); };
            var container = new SubjectId(in.readUTF()); int slots = in.readUnsignedByte();
            var station = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));
            var mission = in.readBoolean() ? Optional.of(new SubjectId(in.readUTF())) : Optional.<SubjectId>empty();
            if (assets.putIfAbsent(actor, new TransportAsset(actor, home, kind, container, slots, station, mission)) != null)
                throw new IllegalArgumentException("duplicate transport asset");
        }
        return new TransportFleet(assets);
    }
}
