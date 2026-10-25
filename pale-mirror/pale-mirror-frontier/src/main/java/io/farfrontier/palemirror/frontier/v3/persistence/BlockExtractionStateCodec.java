package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.io.*;
import java.util.*;

/** One retained prepared-block grammar shared by single-source and composite extraction. */
final class BlockExtractionStateCodec {
    private BlockExtractionStateCodec() { }
    static void write(DataOutputStream out, BlockExtraction effect) throws IOException {
        out.writeUTF(effect.operationId()); ActorExecutionStateCodec.writeId(out, effect.execution());
        FrontierWorldStateCodec.writePosition(out, effect.target()); ExtractionSiteStateCodec.writeDefinition(out, effect.definition());
        out.writeByte(effect.output().size());
        for (var output : effect.output()) { out.writeUTF(output.itemKind()); out.writeByte(output.quantity()); }
    }
    static BlockExtraction read(DataInputStream in) throws IOException {
        String operation = in.readUTF(); var execution = ActorExecutionStateCodec.readId(in);
        var position = FrontierWorldStateCodec.readPosition(in); var definition = ExtractionSiteStateCodec.readDefinition(in);
        int count = in.readUnsignedByte();
        if (count < 1 || count > BlockExtraction.MAX_OUTPUTS) throw new IllegalArgumentException("invalid retained extraction output count");
        var outputs = new ArrayList<BlockExtraction.Output>();
        for (int i = 0; i < count; i++) outputs.add(new BlockExtraction.Output(in.readUTF(), in.readUnsignedByte()));
        return new BlockExtraction(operation, execution, position, definition, outputs);
    }
}
