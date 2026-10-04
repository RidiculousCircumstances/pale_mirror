package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.HiveTaskAssembly;
import io.farfrontier.palemirror.frontier.v3.model.HiveTraversalStep;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/** Current-schema hive routes and captured step evidence share one bounded wire layout. */
final class HiveTraversalCodec {
    private HiveTraversalCodec() { }
    static void writeMember(DataOutputStream output, HiveTaskAssembly.Member member) throws IOException {
        TraversalTopologyStateCodec.write(output, member.topology()); output.writeShort(member.cursor());
        output.writeLong(member.routeRevision()); TraversalRejoinCodec.write(output, member.rejoin());
    }
    static HiveTaskAssembly.Member readMember(DataInputStream input) throws IOException {
        return new HiveTaskAssembly.Member(TraversalTopologyStateCodec.read(input), input.readUnsignedShort(),
                input.readLong(), TraversalRejoinCodec.read(input));
    }
    static void writeStep(DataOutputStream output, HiveTraversalStep step) throws IOException {
        output.writeShort(step.expectedCursor()); output.writeLong(step.routeRevision()); output.writeShort(step.approachCursor());
        output.writeBoolean(step.hotArrival().isPresent());
        if (step.hotArrival().isPresent()) {
            var arrival = step.hotArrival().orElseThrow();
            FrontierWorldPayloadCodecs.writeSubject(output, arrival.body().actorId());
            output.writeLong(arrival.body().physicalEpoch()); output.writeLong(arrival.leaseRevision());
        }
    }
    static HiveTraversalStep readStep(DataInputStream input) throws IOException {
        int cursor = input.readUnsignedShort(); long revision = input.readLong(); int approach = input.readShort();
        Optional<HiveTraversalStep.HotArrival> hot = input.readBoolean()
                ? Optional.of(new HiveTraversalStep.HotArrival(new ActorBodyId(FrontierWorldPayloadCodecs.readSubject(input).value(),
                input.readLong()), input.readLong())) : Optional.empty();
        return new HiveTraversalStep(cursor, revision, approach, hot);
    }
}
