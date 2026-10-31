package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;
/** Current-format guard events; no historical duty or equipment identity inference. */
final class GuardPayloadCodecs {
    static PayloadCodecs create() { return new PayloadCodecs(List.of(codec(GuardDutyEvent.Appointed.TYPE), codec(GuardDutyEvent.Changed.TYPE))); }
    private static PayloadCodec codec(String type) {
        return new PayloadCodec() {
            public String type() { return type; }
            public byte[] encode(FrontierPayload payload) {
                if (!payload.type().equals(type)) throw new IllegalArgumentException("foreign guard payload");
                try { var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
                    if (payload instanceof GuardDutyEvent.Appointed value) GuardDutyCodec.write(out, value.duty());
                    else { var value = (GuardDutyEvent.Changed) payload;
                        out.writeUTF(value.duty().value()); out.writeLong(value.revision()); out.writeByte(value.change().wireTag());
                        out.writeBoolean(value.selection().isPresent());
                        if (value.selection().isPresent()) { var selected = value.selection().orElseThrow();
                            out.writeUTF(selected.item().value()); ShipmentStateCodec.endpoint(out, selected.source()); }
                    }
                    return bytes.toByteArray();
                } catch (IOException error) { throw new IllegalArgumentException("cannot encode guard payload", error); }
            }
            public FrontierPayload decode(byte[] bytes) {
                try { var in = new DataInputStream(new ByteArrayInputStream(bytes));
                    FrontierPayload value = type.equals(GuardDutyEvent.Appointed.TYPE) ? new GuardDutyEvent.Appointed(GuardDutyCodec.read(in))
                            : new GuardDutyEvent.Changed(new SubjectId(in.readUTF()), in.readLong(), GuardDutyEvent.Change.require(in.readUnsignedByte()),
                                in.readBoolean() ? Optional.of(new GuardDutyEvent.Selection(new SubjectId(in.readUTF()), ShipmentStateCodec.endpoint(in))) : Optional.empty());
                    if (in.available() != 0) throw new IllegalArgumentException("trailing guard payload bytes");
                    return value;
                } catch (IOException error) { throw new IllegalArgumentException("malformed guard payload", error); }
            }
        };
    }
    private GuardPayloadCodecs() { }
}
