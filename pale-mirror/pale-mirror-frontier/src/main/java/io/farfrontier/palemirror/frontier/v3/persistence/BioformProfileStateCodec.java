package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BioformAssignment;
import io.farfrontier.palemirror.frontier.v3.model.BioformChassis;
import io.farfrontier.palemirror.frontier.v3.model.BioformMutation;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/** One stable profile fragment shared by snapshot and durable hive-growth payloads. */
final class BioformProfileStateCodec {
    private BioformProfileStateCodec() { }

    static void write(DataOutputStream output, Bioform bioform) throws IOException {
        output.writeByte(bioform.chassis().wireTag()); output.writeByte(bioform.assignment().wireTag()); output.writeByte(bioform.mutations().size());
        for (BioformMutation mutation : bioform.mutations().stream().sorted(Comparator.comparingInt(BioformMutation::wireTag)).toList()) output.writeByte(mutation.wireTag());
        FrontierWorldStateCodec.writePosition(output, bioform.position());
    }

    static Bioform read(DataInputStream input, SubjectId id, SubjectId hive, SubjectId nest) throws IOException {
        BioformChassis chassis = FrontierWireTags.require(BioformChassis.class, input.readUnsignedByte());
        BioformAssignment assignment = FrontierWireTags.require(BioformAssignment.class, input.readUnsignedByte());
        int count = input.readUnsignedByte(); if (count > Bioform.MAX_VISIBLE_MUTATIONS) throw new IllegalArgumentException("invalid bioform mutation count");
        Set<BioformMutation> mutations = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) if (!mutations.add(FrontierWireTags.require(BioformMutation.class, input.readUnsignedByte()))) {
            throw new IllegalArgumentException("duplicate bioform mutation");
        }
        BlockPosition position = FrontierWorldStateCodec.readPosition(input);
        return new Bioform(id, hive, nest, chassis, Set.copyOf(mutations), assignment, position);
    }
}
