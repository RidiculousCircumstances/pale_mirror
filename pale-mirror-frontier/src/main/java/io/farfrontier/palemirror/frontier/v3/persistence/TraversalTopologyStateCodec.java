package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalAvailability;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.TraversalEdgeId;
import io.farfrontier.palemirror.frontier.v3.model.TraversalKind;
import io.farfrontier.palemirror.frontier.v3.model.TraversalNodeId;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopology;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopologyId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Stable bounded snapshot/WAL representation of one immutable traversal topology. */
final class TraversalTopologyStateCodec {
    private TraversalTopologyStateCodec() { }

    static void write(DataOutputStream output, TraversalTopology topology) throws IOException {
        FrontierWorldStateCodec.writeString(output, topology.id().value()); output.writeLong(topology.revision()); FrontierWorldStateCodec.writeString(output, topology.provenance().value());
        FrontierWorldStateCodec.writeCount(output, topology.nodes().size());
        for (var entry : topology.nodes().entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(TraversalNodeId::value))).toList()) {
            FrontierWorldStateCodec.writeString(output, entry.getKey().value()); FrontierWorldStateCodec.writePosition(output, entry.getValue().support());
        }
        FrontierWorldStateCodec.writeCount(output, topology.edges().size());
        for (TraversalTopology.Edge edge : topology.edges()) {
            FrontierWorldStateCodec.writeString(output, edge.id().value()); FrontierWorldStateCodec.writeString(output, edge.from().value()); FrontierWorldStateCodec.writeString(output, edge.to().value());
            output.writeByte(FrontierWireTags.tag(edge.kind())); output.writeByte(edge.capabilities().size());
            for (TraversalCapability capability : edge.capabilities().stream().sorted(java.util.Comparator.comparingInt(FrontierWireTags::tag)).toList()) output.writeByte(FrontierWireTags.tag(capability));
            output.writeByte(edge.grade()); output.writeByte(edge.clearance()); output.writeLong(edge.revision()); output.writeByte(FrontierWireTags.tag(edge.availability()));
        }
    }

    static TraversalTopology read(DataInputStream input) throws IOException {
        TraversalTopologyId id = new TraversalTopologyId(FrontierWorldStateCodec.readString(input)); long revision = input.readLong(); SubjectId provenance = new SubjectId(FrontierWorldStateCodec.readString(input));
        Map<TraversalNodeId, SurfaceAnchor> nodes = new LinkedHashMap<>();
        int nodeCount = FrontierWorldStateCodec.readCount(input);
        if (nodeCount < 2 || nodeCount > TraversalTopology.MAX_NODES) throw new IllegalArgumentException("traversal topology node count is invalid");
        for (int index = 0; index < nodeCount; index++) {
            if (nodes.put(new TraversalNodeId(FrontierWorldStateCodec.readString(input)), new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input))) != null) {
                throw new IllegalArgumentException("duplicate traversal topology node");
            }
        }
        var edges = new ArrayList<TraversalTopology.Edge>();
        int edgeCount = FrontierWorldStateCodec.readCount(input);
        if (edgeCount < 1 || edgeCount > TraversalTopology.MAX_EDGES) throw new IllegalArgumentException("traversal topology edge count is invalid");
        for (int index = 0; index < edgeCount; index++) {
            TraversalEdgeId edgeId = new TraversalEdgeId(FrontierWorldStateCodec.readString(input));
            TraversalNodeId from = new TraversalNodeId(FrontierWorldStateCodec.readString(input));
            TraversalNodeId to = new TraversalNodeId(FrontierWorldStateCodec.readString(input));
            TraversalKind kind = FrontierWireTags.require(TraversalKind.class, input.readUnsignedByte()); int capabilityCount = input.readUnsignedByte();
            if (capabilityCount < 1 || capabilityCount > TraversalCapability.values().length) throw new IllegalArgumentException("traversal capability count is invalid");
            Set<TraversalCapability> capabilities = new LinkedHashSet<>();
            for (int capability = 0; capability < capabilityCount; capability++) {
                if (!capabilities.add(FrontierWireTags.require(TraversalCapability.class, input.readUnsignedByte()))) throw new IllegalArgumentException("duplicate traversal capability");
            }
            edges.add(new TraversalTopology.Edge(edgeId, from, to, kind, capabilities, input.readUnsignedByte(), input.readUnsignedByte(), input.readLong(),
                    FrontierWireTags.require(TraversalAvailability.class, input.readUnsignedByte())));
        }
        return new TraversalTopology(id, revision, provenance, nodes, edges);
    }
}
