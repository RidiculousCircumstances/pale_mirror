package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

/** Explicit current-schema participant identities/knowledge; no reconstruction on recovery. */
final class GoodsParticipantStateCodec {
    private GoodsParticipantStateCodec() { }
    static void write(DataOutputStream out, GoodsParticipantState state) throws IOException {
        out.writeInt(state.participants().size());
        for (var participant : state.participants().values().stream().sorted(Comparator.comparing(p -> p.party().id())).toList()) {
            party(out, participant.party()); out.writeByte(participant.policy().wireTag()); endpoint(out, participant.endpoint());
            out.writeInt(participant.known().size());
            for (var peer : participant.known()) { party(out, peer.party()); endpoint(out, peer.endpoint()); }
            out.writeLong(participant.reviewRevision()); out.writeUTF(participant.decision()); out.writeLong(participant.reviewedAtTick());
        }
    }
    static GoodsParticipantState read(DataInputStream in) throws IOException {
        var result = new HashMap<SubjectId, GoodsParticipant>();
        int count = bounded(in, GoodsParticipantState.MAX_PARTICIPANTS);
        for (int i = 0; i < count; i++) {
            var party = party(in); var policy = GoodsPolicyKind.fromWireTag(in.readUnsignedByte()); var endpoint = endpoint(in);
            var peers = new ArrayList<GoodsParticipant.Counterparty>();
            for (int n = bounded(in, 16); n > 0; n--) peers.add(new GoodsParticipant.Counterparty(party(in), endpoint(in)));
            var participant = new GoodsParticipant(party, policy, endpoint, peers, in.readLong(), in.readUTF(), in.readLong());
            if (result.put(party.id(), participant) != null) throw new IllegalArgumentException("duplicate saved participant");
        }
        return new GoodsParticipantState(result);
    }
    private static int bounded(DataInputStream in, int max) throws IOException {
        int value = in.readInt(); if (value < 0 || value > max) throw new IllegalArgumentException("invalid goods knowledge count"); return value;
    }
    private static void party(DataOutputStream out, GoodsTradeParty value) throws IOException {
        out.writeUTF(value.id().value()); out.writeByte(FrontierWireTags.tag(value.kind()));
    }
    private static GoodsTradeParty party(DataInputStream in) throws IOException {
        return new GoodsTradeParty(new SubjectId(in.readUTF()), FrontierWireTags.require(EconomicOwnerKind.class, in.readUnsignedByte()));
    }
    private static void endpoint(DataOutputStream out, ShipmentEndpoint value) throws IOException {
        ShipmentStateCodec.endpoint(out, value);
    }
    private static ShipmentEndpoint endpoint(DataInputStream in) throws IOException {
        return ShipmentStateCodec.endpoint(in);
    }
}
