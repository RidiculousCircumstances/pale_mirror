package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.ItemCondition;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** One lossless exact-item grammar shared by checkpoints and retained operation payloads. */
final class ExactItemStateCodec {
    private ExactItemStateCodec() { }
    static void write(DataOutputStream output, ExactItemStack item) throws IOException {
        FrontierWorldStateCodec.writeString(output, item.id().value());
        FrontierWorldStateCodec.writeString(output, item.economicOwnerId().value());
        FrontierWorldStateCodec.writeString(output, item.itemKind());
        output.writeByte(item.count());
        FrontierWorldStateCodec.writeCustody(output, item.custody());
        output.writeInt(item.condition().maximumDamage()); output.writeInt(item.condition().damage());
    }
    static ExactItemStack read(DataInputStream input) throws IOException {
        return new ExactItemStack(new SubjectId(FrontierWorldStateCodec.readString(input)),
                new SubjectId(FrontierWorldStateCodec.readString(input)), FrontierWorldStateCodec.readString(input),
                input.readUnsignedByte(), FrontierWorldStateCodec.readCustody(input),
                new ItemCondition(input.readInt(), input.readInt()));
    }
}
