package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.OperationTravelObservation;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;

/** Stable provider tags; decoding never guesses a provider from current world state. */
final class OperationTravelObservationCodec {
    private OperationTravelObservationCodec() { }
    static void write(DataOutputStream out, OperationTravelObservation observation) throws IOException {
        out.writeByte(switch (observation) {
            case OperationTravelObservation.ColdSegment ignored -> 0;
            case OperationTravelObservation.HotSegment ignored -> 1;
            case OperationTravelObservation.ColdApproach ignored -> 2;
        });
        ActorExecutionStateCodec.writeGroup(out, observation.executions());
        OperationTravelStateCodec.write(out, observation.predecessor());
        switch (observation) {
            case OperationTravelObservation.ColdSegment cold -> writeEpochs(out, cold.bodyEpochFences());
            case OperationTravelObservation.ColdApproach cold -> writeEpochs(out, cold.bodyEpochFences());
            case OperationTravelObservation.HotSegment ignored -> { }
        }
        if (observation instanceof OperationTravelObservation.HotSegment hot) {
            FrontierWorldStateCodec.writeString(out, hot.leaseId().value()); out.writeByte(hot.members().size());
            for (var entry : hot.members().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
                FrontierWorldPayloadCodecs.writeSubject(out, entry.getKey()); ActorHotObservationCodec.write(out, entry.getValue());
            }
        }
    }
    static OperationTravelObservation read(DataInputStream in) throws IOException {
        int tag = in.readUnsignedByte();
        if (tag > 2) throw new IllegalArgumentException("unknown travel observation provider");
        var executions = ActorExecutionStateCodec.readGroup(in); var predecessor = OperationTravelStateCodec.read(in);
        return switch (tag) {
            case 0 -> new OperationTravelObservation.ColdSegment(executions, predecessor, readEpochs(in));
            case 2 -> new OperationTravelObservation.ColdApproach(executions, predecessor, readEpochs(in));
            case 1 -> {
                var lease = new SceneLeaseId(FrontierWorldStateCodec.readString(in)); int count = in.readUnsignedByte();
                if (count < 1 || count > 8) throw new IllegalArgumentException("invalid observed travel cohort size");
                var members = new LinkedHashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ActorHotObservation>();
                for (int i = 0; i < count; i++) {
                    var actor = FrontierWorldPayloadCodecs.readSubject(in).value();
                    if (members.put(actor, ActorHotObservationCodec.read(in)) != null)
                        throw new IllegalArgumentException("duplicate observed travel member");
                }
                yield new OperationTravelObservation.HotSegment(executions, predecessor, lease, members);
            }
            default -> throw new IllegalArgumentException("unknown travel observation provider");
        };
    }
    private static void writeEpochs(DataOutputStream out, java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Long> epochs) throws IOException {
        out.writeByte(epochs.size());
        for (var entry : epochs.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            FrontierWorldPayloadCodecs.writeSubject(out, entry.getKey()); out.writeLong(entry.getValue());
        }
    }
    private static java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Long> readEpochs(DataInputStream in) throws IOException {
        int count = in.readUnsignedByte();
        if (count < 1 || count > 8) throw new IllegalArgumentException("invalid physical travel cohort size");
        var epochs = new LinkedHashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Long>();
        for (int i = 0; i < count; i++) {
            var actor = FrontierWorldPayloadCodecs.readSubject(in).value();
            if (epochs.put(actor, in.readLong()) != null) throw new IllegalArgumentException("duplicate physical travel epoch");
        }
        return epochs;
    }
}
