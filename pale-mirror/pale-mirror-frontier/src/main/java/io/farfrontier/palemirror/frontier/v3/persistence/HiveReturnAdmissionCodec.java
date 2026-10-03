package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.HiveReturnAdmission;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Own stable, non-reused tags: independent=0, completed-parent=1, returning-parent=2. */
final class HiveReturnAdmissionCodec {
    private HiveReturnAdmissionCodec() { }
    static void write(DataOutputStream output, HiveReturnAdmission admission) throws IOException {
        switch (admission) {
            case HiveReturnAdmission.Independent ignored -> output.writeByte(0);
            case HiveReturnAdmission.Completed completed -> {
                output.writeByte(1); FrontierWorldPayloadCodecs.writeSubject(output, completed.mobilizationId());
            }
            case HiveReturnAdmission.Returning returning -> {
                output.writeByte(2); FrontierWorldPayloadCodecs.writeSubject(output, returning.mobilizationId());
                HiveMobilizationStateCodec.writeReturnAssembly(output, returning.assembly());
                ActorExecutionStateCodec.writeGroup(output, returning.executions());
            }
        }
    }
    static HiveReturnAdmission read(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 0 -> new HiveReturnAdmission.Independent();
            case 1 -> new HiveReturnAdmission.Completed(FrontierWorldPayloadCodecs.readSubject(input).value());
            case 2 -> new HiveReturnAdmission.Returning(FrontierWorldPayloadCodecs.readSubject(input).value(),
                    HiveMobilizationStateCodec.readReturnAssembly(input), ActorExecutionStateCodec.readGroup(input));
            default -> throw new IllegalArgumentException("unknown hive return admission tag");
        };
    }
}
