package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;

import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** SavedData codec for physical presentation records; canonical domain state stays in {@link PaleMirrorSavedData}. */
final class WorldPresentationCodec {
    private WorldPresentationCodec() { }

    static CompoundTag writeEncounter(EncounterRecord encounter) {
        CompoundTag tag = new CompoundTag();
        tag.putString("profile", encounter.profileId());
        tag.putString("profileVersion", encounter.profileVersion());
        tag.putString("job", encounter.jobId());
        tag.putLong("desiredRevision", encounter.desiredRevision());
        tag.putString("composition", encounter.compositionId());
        tag.putString("state", encounter.state().name());
        tag.putString("diagnostic", encounter.diagnostic());
        ListTag actors = new ListTag();
        encounter.actors().forEach(actor -> {
            CompoundTag value = new CompoundTag();
            value.putString("slot", actor.slotId());
            value.putString("profile", actor.actorProfileId());
            value.putString("entityType", actor.entityTypeId());
            if (actor.entityId() != null) value.putUUID("entity", actor.entityId());
            value.putString("status", actor.status().name());
            value.putLong("nextRuntimeTick", actor.nextRuntimeTick());
            value.putInt("actionCounter", actor.actionCounter());
            value.putInt("combatHitPoints", actor.combatHitPoints());
            value.putLong("nextMovementTick", actor.nextMovementTick());
            value.putInt("routeCursor", actor.routeCursor());
            actors.add(value);
        });
        tag.put("actors", actors);
        return tag;
    }

    static EncounterRecord readEncounter(CompoundTag tag) {
        List<EncounterActorRef> actors = new ArrayList<>();
        for (Tag element : tag.getList("actors", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            actors.add(new EncounterActorRef(value.getString("slot"), value.getString("profile"), value.getString("entityType"),
                    value.hasUUID("entity") ? value.getUUID("entity") : null,
                    EncounterActorRef.Status.valueOf(value.getString("status")), value.getLong("nextRuntimeTick"),
                    value.getInt("actionCounter"), value.contains("combatHitPoints", Tag.TAG_INT)
                            ? value.getInt("combatHitPoints") : EncounterActorRef.UNINITIALIZED_COMBAT_HIT_POINTS,
                    value.getLong("nextMovementTick"), value.getInt("routeCursor")));
        }
        return new EncounterRecord(tag.getString("profile"), tag.getString("profileVersion"), tag.getString("job"),
                tag.getLong("desiredRevision"), tag.getString("composition"), actors,
                EncounterState.valueOf(tag.contains("state", Tag.TAG_STRING) ? tag.getString("state") : "NONE"),
                tag.getString("diagnostic"));
    }

    static CompoundTag writeSiege(SiegeRecord siege) {
        CompoundTag tag = new CompoundTag();
        tag.putString("definition", siege.definitionId());
        tag.putString("definitionVersion", siege.definitionVersion());
        tag.putLong("desiredRevision", siege.desiredRevision());
        tag.putString("diagnostic", siege.diagnostic());
        ListTag parts = new ListTag();
        siege.parts().forEach(part -> {
            CompoundTag value = new CompoundTag();
            value.putString("slot", part.slotId());
            value.putString("kind", part.kind().name());
            value.putString("profile", part.profileId());
            value.putLong("position", part.position().asLong());
            if (part.entityId() != null) value.putUUID("entity", part.entityId());
            value.putString("status", part.status().name());
            parts.add(value);
        });
        tag.put("parts", parts);
        return tag;
    }

    static SiegeRecord readSiege(CompoundTag tag) {
        List<SiegePartRef> parts = new ArrayList<>();
        for (Tag element : tag.getList("parts", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            parts.add(new SiegePartRef(value.getString("slot"), SiegePartKind.valueOf(value.getString("kind")),
                    value.getString("profile"), BlockPos.of(value.getLong("position")),
                    value.hasUUID("entity") ? value.getUUID("entity") : null,
                    SiegePartRef.Status.valueOf(value.getString("status"))));
        }
        return new SiegeRecord(tag.getString("definition"), tag.getString("definitionVersion"),
                tag.getLong("desiredRevision"), parts, tag.getString("diagnostic"));
    }

    static CompoundTag writeRegistryEntry(WorldObjectRegistryEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", entry.id().value());
        tag.putString("dimension", entry.dimensionId());
        tag.putLong("anchor", entry.anchor().asLong());
        tag.putLong("minBounds", entry.minBounds().asLong());
        tag.putLong("maxBounds", entry.maxBounds().asLong());
        tag.putString("template", entry.templateId());
        tag.putString("templateVersion", entry.templateVersion());
        tag.putString("lifecycle", entry.lifecycle().name());
        return tag;
    }

    static WorldObjectRegistry readRegistry(ListTag serialized) {
        WorldObjectRegistry registry = new WorldObjectRegistry();
        for (Tag element : serialized) {
            CompoundTag tag = (CompoundTag) element;
            registry.register(new WorldObjectRegistryEntry(new WorldObjectId(tag.getString("id")), tag.getString("dimension"),
                    BlockPos.of(tag.getLong("anchor")), BlockPos.of(tag.getLong("minBounds")),
                    BlockPos.of(tag.getLong("maxBounds")), tag.getString("template"), tag.getString("templateVersion"),
                    WorldObjectLifecycle.valueOf(tag.getString("lifecycle"))));
        }
        return registry;
    }
}
