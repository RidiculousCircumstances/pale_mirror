package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3BlockExtractionCodecTest {
    @Test void retainedGenericPreparationPreservesExecutionSourceAndOutputAndRejectsIncompleteOrAlteredEvidence() {
        var definition = new BlockExtraction.Definition("pale_mirror:test_rock_v1",
                new BlockExtraction.Block("minecraft:stone", Map.of()),
                new BlockExtraction.Block("minecraft:air", Map.of()), "minecraft:iron_pickaxe", "minecraft:blocks/stone",
                List.of(new BlockExtraction.Output("minecraft:cobblestone", 1)));
        var execution = new ActorExecutionId(new SubjectId("resident:rock-worker"), ActorActivityKind.PRESENCE,
                new SubjectId("task:rock"), 9);
        var prepared = BlockExtraction.prepareKnown("effect:rock:9", execution, new BlockPosition(4, 65, -9), definition, definition.before());
        assertEquals(prepared, FrontierV3BlockExtractionCodec.read(FrontierV3BlockExtractionCodec.write(prepared)));
        for (String missing : List.of("owner", "actor", "activity", "generation", "before", "output")) {
            var tag = FrontierV3BlockExtractionCodec.write(prepared); tag.remove(missing);
            assertThrows(IllegalStateException.class, () -> FrontierV3BlockExtractionCodec.read(tag), missing);
        }
        var unknownKind = FrontierV3BlockExtractionCodec.write(prepared); unknownKind.putInt("activity", Integer.MAX_VALUE);
        assertThrows(IllegalStateException.class, () -> FrontierV3BlockExtractionCodec.read(unknownKind));
        var alteredOutput = FrontierV3BlockExtractionCodec.write(prepared);
        alteredOutput.getList("output", Tag.TAG_COMPOUND).getCompound(0).putInt("quantity", 2);
        assertThrows(IllegalStateException.class, () -> FrontierV3BlockExtractionCodec.read(alteredOutput));
        assertThrows(IllegalArgumentException.class, () -> BlockExtraction.prepareKnown("effect:wrong-source", execution,
                prepared.target(), definition, new BlockExtraction.Block("minecraft:dirt", Map.of())));
    }
}
