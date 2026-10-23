package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.WorkToolAssignment;
import java.io.*;

final class WorkToolAssignmentCodec {
    static void write(DataOutputStream out, WorkToolAssignment value) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(out, value.itemId());
        ShipmentStateCodec.endpoint(out, value.source()); out.writeByte(value.stage().wireTag());
        out.writeBoolean(value.pending().isPresent());
        if (value.pending().isPresent()) WorkEquipmentStepCodec.write(out, value.pending().orElseThrow());
        out.writeBoolean(value.lastUse().isPresent());
        if (value.lastUse().isPresent()) {
            ExactItemStateCodec.write(out, value.lastUse().orElseThrow().before()); out.writeInt(value.lastUse().orElseThrow().wear());
        }
        out.writeBoolean(value.returnTarget().isPresent());
        if (value.returnTarget().isPresent()) ShipmentStateCodec.endpoint(out, value.returnTarget().orElseThrow());
    }
    static WorkToolAssignment read(DataInputStream in) throws IOException {
        return new WorkToolAssignment(FrontierWorldPayloadCodecs.readSubject(in).value(), ShipmentStateCodec.endpoint(in),
                WorkToolAssignment.Stage.decode(in.readUnsignedByte()),
                in.readBoolean() ? java.util.Optional.of(WorkEquipmentStepCodec.read(in)) : java.util.Optional.empty(),
                in.readBoolean() ? java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse(
                        ExactItemStateCodec.read(in), in.readInt())) : java.util.Optional.empty(),
                in.readBoolean() ? java.util.Optional.of(ShipmentStateCodec.endpoint(in)) : java.util.Optional.empty());
    }
    private WorkToolAssignmentCodec() { }
}
