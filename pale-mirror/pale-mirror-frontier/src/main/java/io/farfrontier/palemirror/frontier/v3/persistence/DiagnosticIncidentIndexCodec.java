package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact state codec for the bounded derived incident lookup; no legacy hydration is accepted. */
final class DiagnosticIncidentIndexCodec {
    private DiagnosticIncidentIndexCodec() { }
    static void write(DataOutputStream out, DiagnosticIncidentIndex index) throws IOException {
        FrontierWorldStateCodec.writeCount(out, index.incidents().size());
        for (DiagnosticIncident value : index.incidents().values().stream().sorted(java.util.Comparator.comparing(DiagnosticIncident::id)).toList()) {
            FrontierWorldStateCodec.writeString(out, value.id()); writeTuple(out, value.diagnostic());
            FrontierWorldStateCodec.writeString(out, value.firstEventId()); FrontierWorldStateCodec.writeString(out, value.firstCauseId());
            out.writeLong(value.firstRevision()); out.writeLong(value.firstInstant()); out.writeLong(value.lastRevision()); out.writeLong(value.lastInstant()); out.writeInt(value.occurrences()); out.writeBoolean(value.awaitingReview()); writeContext(out, value.context());
        }
        FrontierWorldStateCodec.writeCount(out, index.bySubject().size());
        for (Map.Entry<DiagnosticIncidentIndex.SubjectKey, String> value : index.bySubject().entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(key -> key.kind().name() + "\\u0000" + key.id().value()))).toList()) {
            out.writeByte(DiagnosticWireTags.subjectTag(value.getKey().kind())); FrontierWorldStateCodec.writeString(out, value.getKey().id().value()); FrontierWorldStateCodec.writeString(out, value.getValue());
        }
        out.writeInt(index.droppedOptional());
    }
    static DiagnosticIncidentIndex read(DataInputStream in) throws IOException {
        Map<String, DiagnosticIncident> incidents = new LinkedHashMap<>();
        int count = FrontierWorldStateCodec.readCount(in); if (count > DiagnosticIncidentIndex.MAX_INCIDENTS) throw new IllegalArgumentException("diagnostic incident retention limit exceeded");
        for (int i = 0; i < count; i++) {
            String id = FrontierWorldStateCodec.readString(in); DiagnosticTuple tuple = readTuple(in);
            DiagnosticIncident value = new DiagnosticIncident(id, tuple, FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readInt(), in.readBoolean(), readContext(in));
            if (incidents.put(id, value) != null) throw new IllegalArgumentException("duplicate diagnostic incident id");
        }
        Map<DiagnosticIncidentIndex.SubjectKey, String> subjects = new LinkedHashMap<>();
        int indexed = FrontierWorldStateCodec.readCount(in); if (indexed > DiagnosticIncidentIndex.MAX_INCIDENTS) throw new IllegalArgumentException("diagnostic subject index limit exceeded");
        for (int i = 0; i < indexed; i++) {
            DiagnosticIncidentIndex.SubjectKey key = new DiagnosticIncidentIndex.SubjectKey(DiagnosticWireTags.subjectKind(in.readUnsignedByte()), new SubjectId(FrontierWorldStateCodec.readString(in)));
            if (subjects.put(key, FrontierWorldStateCodec.readString(in)) != null) throw new IllegalArgumentException("duplicate diagnostic subject index");
        }
        return new DiagnosticIncidentIndex(incidents, subjects, in.readInt());
    }
    private static void writeContext(DataOutputStream out, DiagnosticIncidentContext value) throws IOException {
        FrontierWorldStateCodec.writeString(out, value.world()); FrontierWorldStateCodec.writeString(out, value.runtime()); FrontierWorldStateCodec.writeString(out, value.sourceTree()); FrontierWorldStateCodec.writeString(out, value.jar()); FrontierWorldStateCodec.writeString(out, value.ruleset()); FrontierWorldStateCodec.writeString(out, value.restartIdentity()); FrontierWorldStateCodec.writeString(out, value.causalWindow()); FrontierWorldStateCodec.writeString(out, value.physical()); FrontierWorldStateCodec.writeString(out, value.claim()); FrontierWorldStateCodec.writeString(out, value.intent()); FrontierWorldStateCodec.writeString(out, value.observation()); FrontierWorldStateCodec.writeString(out, value.reconciliation()); FrontierWorldStateCodec.writeString(out, value.projection()); out.writeBoolean(value.complete()); FrontierWorldStateCodec.writeString(out, value.degradation());
    }
    private static DiagnosticIncidentContext readContext(DataInputStream in) throws IOException {
        return new DiagnosticIncidentContext(FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), FrontierWorldStateCodec.readString(in), in.readBoolean(), FrontierWorldStateCodec.readString(in));
    }
    private static void writeTuple(DataOutputStream out, DiagnosticTuple value) throws IOException {
        out.writeShort(value.reason().wireTag()); out.writeByte(value.category().wireTag()); out.writeByte(DiagnosticWireTags.ownerTag(value.owner().kind())); FrontierWorldStateCodec.writeString(out, value.owner().id().value()); out.writeByte(DiagnosticWireTags.subjectTag(value.subject().kind())); FrontierWorldStateCodec.writeString(out, value.subject().id().value()); out.writeByte(value.disposition().wireTag());
    }
    private static DiagnosticTuple readTuple(DataInputStream in) throws IOException {
        return new DiagnosticTuple(DiagnosticWireTags.reason(in.readUnsignedShort()), DiagnosticWireTags.category(in.readUnsignedByte()), new DiagnosticOwner(DiagnosticWireTags.ownerKind(in.readUnsignedByte()), new SubjectId(FrontierWorldStateCodec.readString(in))), new DiagnosticSubject(DiagnosticWireTags.subjectKind(in.readUnsignedByte()), new SubjectId(FrontierWorldStateCodec.readString(in))), DiagnosticWireTags.disposition(in.readUnsignedByte()));
    }
}
