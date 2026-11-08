package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;

final class SeatPayloadCodecs {
    static PayloadCodecs codecs() {
        return new PayloadCodecs(List.of(SeatEvent.Reserved.TYPE, SeatEvent.Prepared.TYPE, SeatEvent.Checkpointed.TYPE, SeatEvent.ApplicationCheckpointed.TYPE, SeatEvent.Changed.TYPE, SeatEvent.Released.TYPE)
                .stream().map(SeatPayloadCodecs::codec).toList());
    }
    private static PayloadCodec codec(String type) { return new PayloadCodec() {
        public String type() { return type; }
        public byte[] encode(FrontierPayload value) {
            if (!type.equals(value.type())) throw new IllegalArgumentException("foreign seat payload");
            try {
                var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
                switch (value) {
                    case SeatEvent.Reserved e -> SeatStateCodec.use(out, e.use());
                    case SeatEvent.Prepared e -> { id(out, e.actor()); out.writeLong(e.revision()); actuation(out, e.body()); out.writeByte(e.action().wireTag()); out.writeByte(e.mode().wireTag()); }
                    case SeatEvent.Checkpointed e -> {
                        id(out, e.actor()); out.writeLong(e.revision()); actuation(out, e.body()); out.writeByte(e.action().wireTag());
                        out.writeByte(e.mode().wireTag()); FrontierWorldPayloadCodecs.writeBody(out, e.observed());
                    }
                    case SeatEvent.ApplicationCheckpointed e -> {
                        id(out, e.actor()); out.writeLong(e.revision()); actuation(out, e.body()); out.writeByte(e.action().wireTag());
                        out.writeByte(e.mode().wireTag()); out.writeUTF(e.operation().toString()); FrontierWorldPayloadCodecs.writeBody(out, e.observed());
                    }
                    case SeatEvent.Changed e -> {
                        id(out, e.actor()); out.writeLong(e.revision()); out.writeBoolean(e.body().isPresent());
                        if (e.body().isPresent()) actuation(out, e.body().orElseThrow());
                        out.writeByte(e.action().wireTag()); out.writeByte(e.mode().wireTag()); out.writeBoolean(e.applied()); FrontierWorldPayloadCodecs.writeBody(out, e.observed());
                    }
                    case SeatEvent.Released e -> { id(out, e.actor()); out.writeLong(e.revision()); }
                    default -> throw new IllegalArgumentException("unknown seat payload");
                }
                return bytes.toByteArray();
            } catch (IOException error) { throw new IllegalArgumentException("seat encoding failed", error); }
        }
        public FrontierPayload decode(byte[] bytes) {
            try {
                var in = new DataInputStream(new ByteArrayInputStream(bytes));
                FrontierPayload value = switch (type) {
                    case SeatEvent.Reserved.TYPE -> new SeatEvent.Reserved(SeatStateCodec.use(in));
                    case SeatEvent.Prepared.TYPE -> new SeatEvent.Prepared(id(in), in.readLong(), actuation(in), SeatUse.Action.fromWireTag(in.readUnsignedByte()), SeatUse.ExecutionMode.fromWireTag(in.readUnsignedByte()));
                    case SeatEvent.Checkpointed.TYPE -> new SeatEvent.Checkpointed(id(in), in.readLong(), actuation(in),
                            SeatUse.Action.fromWireTag(in.readUnsignedByte()), SeatUse.ExecutionMode.fromWireTag(in.readUnsignedByte()), FrontierWorldPayloadCodecs.readBody(in));
                    case SeatEvent.ApplicationCheckpointed.TYPE -> new SeatEvent.ApplicationCheckpointed(id(in), in.readLong(), actuation(in),
                            SeatUse.Action.fromWireTag(in.readUnsignedByte()), SeatUse.ExecutionMode.fromWireTag(in.readUnsignedByte()),
                            UUID.fromString(in.readUTF()), FrontierWorldPayloadCodecs.readBody(in));
                    case SeatEvent.Changed.TYPE -> new SeatEvent.Changed(id(in), in.readLong(), in.readBoolean() ? Optional.of(actuation(in)) : Optional.empty(),
                            SeatUse.Action.fromWireTag(in.readUnsignedByte()), SeatUse.ExecutionMode.fromWireTag(in.readUnsignedByte()), in.readBoolean(), FrontierWorldPayloadCodecs.readBody(in));
                    case SeatEvent.Released.TYPE -> new SeatEvent.Released(id(in), in.readLong());
                    default -> throw new IllegalArgumentException("unknown seat payload tag");
                };
                if (in.available() != 0) throw new IllegalArgumentException("trailing seat data");
                return value;
            } catch (IOException error) { throw new IllegalArgumentException("truncated seat payload", error); }
        }
    }; }
    private SeatPayloadCodecs() { }
}
