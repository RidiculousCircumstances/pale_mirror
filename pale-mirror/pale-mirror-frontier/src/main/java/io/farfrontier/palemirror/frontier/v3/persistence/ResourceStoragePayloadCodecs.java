package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

final class ResourceStoragePayloadCodecs {
    static List<PayloadCodec> codecs() { return List.of(new Codec(true), new Codec(false), new AdoptionCodec()); }
    private record AdoptionCodec() implements PayloadCodec {
        @Override public String type() { return "frontier.resource_storage_adopted"; }
        @Override public byte[] encode(FrontierPayload payload) {
            try {
                var value = (ResourceStorageAdopted) payload;
                var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
                var declaration = new Codec(true).encode(value.declaration());
                out.writeInt(declaration.length); out.write(declaration); out.writeByte(value.contents().size());
                for (var lot : value.contents()) {
                    out.writeUTF(lot.id().value()); out.writeUTF(lot.economicOwnerId().value()); out.writeUTF(lot.itemKind());
                    out.writeByte(lot.quantity()); out.writeUTF(lot.provenance());
                }
                return bytes.toByteArray();
            } catch (IOException error) { throw new IllegalArgumentException("unable to encode storage adoption", error); }
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            try {
                var in = new DataInputStream(new ByteArrayInputStream(bytes)); int size = in.readInt();
                if (size < 1 || size > in.available()) throw new IllegalArgumentException("invalid adoption declaration length");
                var declaration = (ResourceStorageDeclared) new Codec(true).decode(in.readNBytes(size));
                int count = in.readUnsignedByte();
                if (count > declaration.container().slotCount()) throw new IllegalArgumentException("unbounded adopted contents");
                var lots = new ArrayList<ResourceLot>();
                for (int i = 0; i < count; i++) lots.add(new ResourceLot(new SubjectId(in.readUTF()), new SubjectId(in.readUTF()),
                        in.readUTF(), in.readUnsignedByte(), in.readUTF(), List.of()));
                if (in.available() != 0) throw new IllegalArgumentException("trailing storage adoption payload");
                return new ResourceStorageAdopted(declaration, lots);
            } catch (IOException error) { throw new IllegalArgumentException("malformed storage adoption payload", error); }
        }
    }
    private record Codec(boolean declaration) implements PayloadCodec {
        @Override public String type() { return declaration ? "frontier.resource_storage_declared" : "frontier.resource_storage_policy_changed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            try {
                var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
                if (declaration) {
                    var value = (ResourceStorageDeclared) payload;
                    ContainerRecordStateCodec.write(out, Map.of(value.container().id(), value.container()));
                    FrontierWorldStateCodec.writePosition(out, value.position());
                } else {
                    var value = (ResourceStoragePolicyChanged) payload; out.writeUTF(value.containerId().value());
                    ResourceStoragePolicyCodec.write(out, value.expected()); ResourceStoragePolicyCodec.write(out, value.replacement());
                }
                return bytes.toByteArray();
            } catch (IOException error) { throw new IllegalArgumentException("unable to encode storage capability", error); }
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            try {
                var in = new DataInputStream(new ByteArrayInputStream(bytes)); FrontierPayload value;
                if (declaration) {
                    var records = ContainerRecordStateCodec.read(in);
                    if (records.size() != 1) throw new IllegalArgumentException("storage event declares exactly one container");
                    value = new ResourceStorageDeclared(records.values().iterator().next(), FrontierWorldStateCodec.readPosition(in));
                } else value = new ResourceStoragePolicyChanged(new SubjectId(in.readUTF()), ResourceStoragePolicyCodec.read(in), ResourceStoragePolicyCodec.read(in));
                if (in.available() != 0) throw new IllegalArgumentException("trailing storage capability payload"); return value;
            } catch (IOException error) { throw new IllegalArgumentException("malformed storage capability payload", error); }
        }
    }
    private ResourceStoragePayloadCodecs() { }
}
