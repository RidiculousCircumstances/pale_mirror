package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import java.io.*;
final class PhysicalIntentStorageConfirmedCodec implements PayloadCodec {
    public String type(){return PhysicalIntentStorageConfirmed.TYPE;}
    public byte[] encode(FrontierPayload payload){var v=(PhysicalIntentStorageConfirmed)payload;return FrontierWorldPayloadCodecs.encodeProduction(out->{
        PhysicalIntentPayloadCodec.write(out,v.expected());PhysicalEffectObservationPayloadCodec.write(out,v.observation());PhysicalReplicaCustodyPayloadCodecs.writeLease(out,v.storage());out.writeUTF(v.fingerprint());
                out.writeUTF(v.provenance());
    });}
    public FrontierPayload decode(byte[] bytes){return FrontierWorldPayloadCodecs.decodeProduction(bytes,in->new PhysicalIntentStorageConfirmed(PhysicalIntentPayloadCodec.read(in),PhysicalEffectObservationPayloadCodec.read(in),
            PhysicalReplicaCustodyPayloadCodecs.readLease(in),in.readUTF(),in.readUTF()));}
}
