package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3GeneratedWorksitesTest {
    @Test void immutableInitialInfrastructureKeepsExactIdentityAndRejectsRuntimeState() {
        var position = new BlockPos(2, 60, 3);
        var cell = new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.EXTRACTIVE_SITE,
                new SubjectId("extraction:test"), WorksiteBlock.Role.INFRASTRUCTURE, 7),
                new BlockPosition(2, 60, 3), 1, new BlockExtraction.Block("minecraft:stone", Map.of()));
        var value = new FrontierV3GeneratedWorksites("configuration", 42, Map.of(position, cell));
        var encoded = (CompoundTag) FrontierV3GeneratedWorksites.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        assertEquals(value, FrontierV3GeneratedWorksites.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3GeneratedWorksites("configuration", 42, Map.of(position.above(), cell)));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3GeneratedWorksites("configuration", 42,
                Map.of(position, new WorksiteBlock(cell.key(), cell.position(), 2, cell.block()))));
        encoded.getList("cells", Tag.TAG_COMPOUND).getCompound(0).putInt("phase", 1);
        assertThrows(RuntimeException.class, () -> FrontierV3GeneratedWorksites.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
    }
}
