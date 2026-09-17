package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships;
import io.farfrontier.palemirror.frontier.v3.model.RelationshipIncident;
import io.farfrontier.palemirror.frontier.v3.model.TerminalProductionReceipt;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Schema-159 relation facts retained by the market-order owner. */
final class FrontierMarketRelationCodec {
    private FrontierMarketRelationCodec() {}

    static void writeTerminalProductionReceipt(DataOutputStream output, TerminalProductionReceipt receipt) throws IOException {
        FrontierWorldStateCodec.writeString(output, receipt.jobId().value());
        FrontierWorldStateCodec.writeString(output, receipt.workerId().value());
        FrontierWorldStateCodec.writeString(output, receipt.inputId().value());
        FrontierWorldStateCodec.writeString(output, receipt.outputId().value());
        FrontierWorldStateCodec.writeString(output, receipt.inputRepresentation().name());
        FrontierWorldStateCodec.writeString(output, receipt.outputRepresentation().name());
        FrontierWorldStateCodec.writeString(output, receipt.outputKind());
        output.writeInt(receipt.outputCount());
        FrontierWorldStateCodec.writeString(output, receipt.topologyId().value()); output.writeLong(receipt.topologyRevision()); output.writeInt(receipt.traversalCursor());
        output.writeInt(receipt.terminalBody().x()); output.writeInt(receipt.terminalBody().y()); output.writeInt(receipt.terminalBody().z());
    }
    static TerminalProductionReceipt readTerminalProductionReceipt(DataInputStream input) throws IOException {
        SubjectId job = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId worker = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId source = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId output = new SubjectId(FrontierWorldStateCodec.readString(input));
        var inputKind = TerminalProductionReceipt.ResourceRepresentation.valueOf(FrontierWorldStateCodec.readString(input));
        var outputKind = TerminalProductionReceipt.ResourceRepresentation.valueOf(FrontierWorldStateCodec.readString(input));
        String kind = FrontierWorldStateCodec.readString(input); int count = input.readInt();
        var topology = new io.farfrontier.palemirror.frontier.v3.model.TraversalTopologyId(FrontierWorldStateCodec.readString(input)); long revision = input.readLong(); int cursor = input.readInt();
        var body = new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(input.readInt(), input.readInt(), input.readInt());
        return new TerminalProductionReceipt(job, worker, source, output, inputKind, outputKind, kind, count, topology, revision, cursor, body);
    }
    static void writeRelationshipIncident(DataOutputStream output, RelationshipIncident incident) throws IOException {
        FrontierWorldStateCodec.writeString(output, incident.kind().tag());
        FrontierWorldStateCodec.writeString(output, incident.ownerId().value());
        FrontierWorldStateCodec.writeString(output, incident.sourceId().value());
        FrontierWorldStateCodec.writeString(output, incident.expected());
        FrontierWorldStateCodec.writeString(output, incident.observed());
        FrontierWorldStateCodec.writeString(output, incident.reason().name());
        FrontierWorldStateCodec.writeString(output, incident.disposition().name());
        output.writeLong(incident.canonicalRevision());
        FrontierWorldStateCodec.writeString(output, incident.traceCorrelation());
    }
    static RelationshipIncident readRelationshipIncident(DataInputStream input) throws IOException {
        String tag = FrontierWorldStateCodec.readString(input);
        SubjectId owner = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId source = new SubjectId(FrontierWorldStateCodec.readString(input));
        String expected = FrontierWorldStateCodec.readString(input);
        String observed = FrontierWorldStateCodec.readString(input);
        String reason = FrontierWorldStateCodec.readString(input);
        String disposition = FrontierWorldStateCodec.readString(input);
        long revision = input.readLong();
        String trace = FrontierWorldStateCodec.readString(input);
        FrontierDomainRelationships.Kind kind = java.util.Arrays.stream(FrontierDomainRelationships.Kind.values())
                .filter(value -> value.tag().equals(tag)).findFirst().orElseThrow(() -> new IllegalArgumentException("unknown relationship incident tag"));
        return new RelationshipIncident(kind, owner, source, expected, observed, FrontierDomainRelationships.IncidentReason.valueOf(reason),
                FrontierDomainRelationships.Disposition.valueOf(disposition), revision, trace);
    }
}
