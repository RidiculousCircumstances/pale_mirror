package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.geology.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockVolume;
import java.io.*;
import java.util.*;

/** Declaration and survey persistence, never regenerated from worksite coordinates on recovery. */
final class MineralGeologyCodec {
    private MineralGeologyCodec() { }
    static void write(DataOutputStream out, MineralGeology geology, GeologicalKnowledge knowledge) throws IOException {
        out.writeInt(geology.deposits().size());
        for (var deposit : geology.deposits().values().stream().sorted(Comparator.comparing(MineralDeposit::id)).toList()) {
            out.writeUTF(deposit.id().value()); out.writeLong(deposit.revision()); out.writeInt(deposit.sections().size());
            for (var section : deposit.sections()) {
                FrontierWorldStateCodec.writePosition(out, section.volume().min()); FrontierWorldStateCodec.writePosition(out, section.volume().max());
                ExtractionSiteStateCodec.writeBlock(out, section.material());
            }
        }
        out.writeInt(knowledge.surveys().size());
        for (var home : knowledge.surveys().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            out.writeUTF(home.getKey().value()); out.writeInt(home.getValue().size());
            for (var survey : home.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                out.writeUTF(survey.getKey().value()); out.writeLong(survey.getValue());
            }
        }
    }
    record Read(MineralGeology geology, GeologicalKnowledge knowledge) { }
    static Read read(DataInputStream in) throws IOException {
        var deposits = new LinkedHashMap<SubjectId, MineralDeposit>();
        for (int i = 0, n = count(in, MineralGeology.MAX_DEPOSITS); i < n; i++) {
            var id = new SubjectId(in.readUTF()); long revision = in.readLong(); var sections = new ArrayList<MineralDeposit.Section>();
            for (int j = 0, size = count(in, MineralDeposit.MAX_SECTIONS); j < size; j++)
                sections.add(new MineralDeposit.Section(new BlockVolume(FrontierWorldStateCodec.readPosition(in),
                        FrontierWorldStateCodec.readPosition(in)), ExtractionSiteStateCodec.readBlock(in)));
            if (deposits.putIfAbsent(id, new MineralDeposit(id, revision, sections)) != null)
                throw new IllegalArgumentException("duplicate mineral deposit");
        }
        var surveys = new LinkedHashMap<SubjectId, Map<SubjectId, Long>>();
        for (int i = 0, n = count(in, 256); i < n; i++) {
            var home = new SubjectId(in.readUTF()); var known = new LinkedHashMap<SubjectId, Long>();
            for (int j = 0, size = count(in, MineralGeology.MAX_DEPOSITS); j < size; j++)
                if (known.putIfAbsent(new SubjectId(in.readUTF()), in.readLong()) != null)
                    throw new IllegalArgumentException("duplicate geological survey");
            if (surveys.putIfAbsent(home, known) != null) throw new IllegalArgumentException("duplicate geological knowledge owner");
        }
        var geology = new MineralGeology(deposits); var knowledge = new GeologicalKnowledge(surveys); knowledge.validate(geology);
        return new Read(geology, knowledge);
    }
    private static int count(DataInputStream in, int max) throws IOException {
        int value = in.readInt(); if (value < 0 || value > max) throw new IllegalArgumentException("invalid bounded geology count"); return value;
    }
}
