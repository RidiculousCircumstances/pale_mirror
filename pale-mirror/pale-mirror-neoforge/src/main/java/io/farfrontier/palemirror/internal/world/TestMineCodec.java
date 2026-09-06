package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class TestMineCodec {
    private TestMineCodec() { }

    static CompoundTag write(TestMineRecord mine) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", mine.id().value());
        tag.putString("audience", mine.primaryAudience().value());
        if (mine.anchorId() != null) tag.putUUID("anchor", mine.anchorId());
        tag.put("encounter", WorldPresentationCodec.writeEncounter(mine.encounter()));
        tag.put("gatePresentation", WorldPresentationCodec.writeGate(mine.gate()));
        ListTag cells = new ListTag();
        mine.mutableCells().forEach(cell -> {
            CompoundTag value = new CompoundTag();
            value.putLong("pos", cell.position().asLong());
            value.putString("baseline", cell.baselineBlock());
            value.putString("infectionStage", cell.infectionStage().name());
            value.putString("lastApplied", cell.lastAppliedBlock());
            value.putBoolean("conflicted", cell.conflicted());
            cells.add(value);
        });
        tag.put("cells", cells);
        return tag;
    }

    static TestMineRecord read(CompoundTag tag, WorldObjectRegistry registry) {
        List<MutableCell> cells = new ArrayList<>();
        for (Tag element : tag.getList("cells", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            cells.add(new MutableCell(BlockPos.of(value.getLong("pos")), value.getString("baseline"),
                    value.getString("lastApplied"), value.getBoolean("conflicted"),
                    InfectionBiomeStage.valueOf(value.getString("infectionStage"))));
        }
        UUID anchor = tag.hasUUID("anchor") ? tag.getUUID("anchor") : null;
        EncounterRecord encounter = WorldPresentationCodec.readEncounter(tag.getCompound("encounter"));
        GatePresentationRecord gate = WorldPresentationCodec.readGate(tag.getCompound("gatePresentation"));
        StoryAudienceId audience = new StoryAudienceId(tag.getString("audience"));
        WorldObjectId id = new WorldObjectId(tag.getString("id"));
        return new TestMineRecord(registry.require(id), audience, cells, anchor, encounter, gate, null);
    }
}
