package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.PatrolAssembly;
import io.farfrontier.palemirror.frontier.v3.model.PatrolTravel;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopology;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable current-schema encoding for exact patrol ingress and travel cursors. */
final class PatrolStateCodec {
    private PatrolStateCodec() { }

    static void writeAssembly(DataOutputStream output, PatrolAssembly assembly) throws IOException {
        FrontierWorldStateCodec.writeCount(output, assembly.members().size());
        for (var entry : assembly.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            subject(output, entry.getKey()); TraversalTopologyStateCodec.write(output, entry.getValue().topology());
            FrontierWorldStateCodec.writeCount(output, entry.getValue().cursor());
        }
    }

    static PatrolAssembly readAssembly(DataInputStream input) throws IOException {
        Map<SubjectId, PatrolAssembly.Member> members = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId actor = subject(input);
            if (members.put(actor, new PatrolAssembly.Member(TraversalTopologyStateCodec.read(input), FrontierWorldStateCodec.readCount(input))) != null) {
                throw new IllegalArgumentException("duplicate patrol assembly member");
            }
        }
        return new PatrolAssembly(members);
    }

    static void writeTravel(DataOutputStream output, PatrolTravel travel) throws IOException {
        subject(output, travel.leaderId()); TraversalTopologyStateCodec.write(output, travel.leaderRoute());
        FrontierWorldStateCodec.writeCount(output, travel.members().size());
        for (var entry : travel.members().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            subject(output, entry.getKey()); TraversalTopologyStateCodec.write(output, entry.getValue().topology());
            FrontierWorldStateCodec.writeCount(output, entry.getValue().cursor());
        }
    }

    static PatrolTravel readTravel(DataInputStream input) throws IOException {
        SubjectId leader = subject(input); TraversalTopology route = TraversalTopologyStateCodec.read(input);
        Map<SubjectId, PatrolTravel.Member> members = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId actor = subject(input);
            if (members.put(actor, new PatrolTravel.Member(TraversalTopologyStateCodec.read(input), FrontierWorldStateCodec.readCount(input))) != null) {
                throw new IllegalArgumentException("duplicate patrol travel member");
            }
        }
        return new PatrolTravel(leader, route, members);
    }

    private static void subject(DataOutputStream output, SubjectId id) throws IOException { FrontierWorldStateCodec.writeString(output, id.value()); }
    private static SubjectId subject(DataInputStream input) throws IOException { return new SubjectId(FrontierWorldStateCodec.readString(input)); }
}
