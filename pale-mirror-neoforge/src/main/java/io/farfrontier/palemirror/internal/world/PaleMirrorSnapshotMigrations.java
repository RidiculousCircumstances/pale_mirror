package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.SiegeStage;
import io.farfrontier.palemirror.domain.ThreatTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Sequential, explicit migration steps for PM canonical snapshots. */
final class PaleMirrorSnapshotMigrations {
    private PaleMirrorSnapshotMigrations() { }

    static boolean isMigratable(int version) {
        return version == PaleMirrorSavedData.CURRENT_SCHEMA || version == 5 || version == 6
                || version == 7 || version == 8 || version == 9 || version == 10 || version == 11 || version == 12
                || version == 13 || version == 14;
    }

    static CompoundTag migrate(CompoundTag source) {
        int version = source.contains("schemaVersion", Tag.TAG_INT) ? source.getInt("schemaVersion") : 0;
        if (version > PaleMirrorSavedData.CURRENT_SCHEMA || !isMigratable(version)) throw incompatibleSchema(version);
        CompoundTag migrated = source.copy();
        if (version == 5) { migrateV5ToV6(migrated); version = 6; }
        if (version == 6) { migrateV6ToV7(migrated); version = 7; }
        if (version == 7) { migrateV7ToV8(migrated); version = 8; }
        if (version == 8) { migrateV8ToV9(migrated); version = 9; }
        if (version == 9) { migrateV9ToV10(migrated); version = 10; }
        if (version == 10) { migrateV10ToV11(migrated); version = 11; }
        if (version == 11) { migrateV11ToV12(migrated); version = 12; }
        if (version == 12) { migrateV12ToV13(migrated); version = 13; }
        if (version == 13) { migrateV13ToV14(migrated); version = 14; }
        if (version == 14) migrateV14ToV15(migrated);
        return migrated;
    }

    private static IllegalStateException incompatibleSchema(int version) {
        return new IllegalStateException("Pale Mirror data schema " + version + " cannot be migrated to schema "
                + PaleMirrorSavedData.CURRENT_SCHEMA + ". Back up the world and remove its data/pale_mirror.dat to deliberately reset legacy Pale Mirror state.");
    }

    private static void migrateV5ToV6(CompoundTag tag) {
        for (Tag element : tag.getList("testMines", Tag.TAG_COMPOUND)) {
            CompoundTag mine = (CompoundTag) element;
            if (mine.hasUUID("controller") && !mine.hasUUID("anchor")) mine.putUUID("anchor", mine.getUUID("controller"));
            if (mine.contains("job", Tag.TAG_COMPOUND)) for (Tag operationElement : mine.getCompound("job").getList("operations", Tag.TAG_COMPOUND)) {
                CompoundTag operation = (CompoundTag) operationElement;
                operation.putString("type", switch (operation.getString("type")) {
                    case "ENSURE_TEST_THREAT_CONTROLLER" -> "ENSURE_PM_ANCHOR";
                    case "REMOVE_TEST_THREAT_CONTROLLER" -> "REMOVE_PM_ANCHOR";
                    default -> operation.getString("type");
                });
                if (!operation.contains("target", Tag.TAG_STRING)) operation.putString("target", "");
            }
        }
        for (Tag element : tag.getCompound("snapshot").getList("scenarios", Tag.TAG_COMPOUND)) {
            CompoundTag scenario = (CompoundTag) element;
            if (!scenario.contains("encounterProfile", Tag.TAG_STRING)) {
                boolean defaultMineScenario = "pale_mirror:investigation_recovery".equals(scenario.getString("definition"));
                scenario.putString("encounterProfile", defaultMineScenario ? "pale_mirror:crimson_mine_guards" : "");
                scenario.putString("encounterProfileVersion", defaultMineScenario ? "1" : "");
            }
            ListTag capabilities = scenario.getList("requiredCapabilities", Tag.TAG_STRING);
            for (int index = 0; index < capabilities.size(); index++) {
                String value = capabilities.getString(index);
                if ("TEST_THREAT_MATERIALIZATION".equals(value)) capabilities.set(index, net.minecraft.nbt.StringTag.valueOf("PM_ANCHOR_MATERIALIZATION"));
                if ("TEST_THREAT_OBSERVATION".equals(value)) capabilities.set(index, net.minecraft.nbt.StringTag.valueOf("PM_ANCHOR_OBSERVATION"));
            }
        }
        tag.putInt("schemaVersion", 6);
    }

    private static void migrateV6ToV7(CompoundTag tag) {
        CompoundTag snapshot = tag.getCompound("snapshot");
        long step = snapshot.getLong("simulationStep");
        for (Tag element : snapshot.getList("facilities", Tag.TAG_COMPOUND)) {
            CompoundTag facility = (CompoundTag) element;
            boolean infected = "INFECTED".equals(facility.getString("status"));
            facility.putString("threatTier", infected ? ThreatTier.FOOTHOLD.name() : ThreatTier.DORMANT.name());
            facility.putLong("threatStartedAtStep", infected ? step : 0L);
        }
        for (Tag element : tag.getList("testMines", Tag.TAG_COMPOUND)) {
            CompoundTag mine = (CompoundTag) element;
            if (!mine.contains("encounter", Tag.TAG_COMPOUND)) continue;
            for (Tag actorElement : mine.getCompound("encounter").getList("actors", Tag.TAG_COMPOUND)) {
                CompoundTag actor = (CompoundTag) actorElement;
                actor.putString("profile", "minecraft:zombie".equals(actor.getString("entityType"))
                        ? "pale_mirror:crimsonified_human" : "");
                actor.putLong("nextRuntimeTick", 0L);
                actor.putInt("actionCounter", 0);
            }
        }
        tag.putInt("schemaVersion", 7);
    }

    private static void migrateV7ToV8(CompoundTag tag) {
        for (Tag element : tag.getCompound("snapshot").getList("facilities", Tag.TAG_COMPOUND)) {
            CompoundTag facility = (CompoundTag) element;
            if (facility.contains("siege", Tag.TAG_COMPOUND)) continue;
            CompoundTag siege = new CompoundTag();
            siege.putString("stage", "INFECTED".equals(facility.getString("status")) ? SiegeStage.BYPASSED.name() : SiegeStage.INACTIVE.name());
            siege.putString("definition", "");
            siege.putString("definitionVersion", "");
            siege.putString("boss", "");
            siege.put("destroyedNodes", new ListTag());
            facility.put("siege", siege);
        }
        tag.putInt("schemaVersion", 8);
    }

    private static void migrateV8ToV9(CompoundTag tag) {
        for (Tag mineElement : tag.getList("testMines", Tag.TAG_COMPOUND)) for (Tag cellElement : ((CompoundTag) mineElement)
                .getList("cells", Tag.TAG_COMPOUND)) {
            CompoundTag cell = (CompoundTag) cellElement;
            if (!cell.contains("infectionStage", Tag.TAG_STRING)) cell.putString("infectionStage", InfectionBiomeStage.NODE.name());
        }
        tag.putInt("schemaVersion", 9);
    }

    /** v10 adds explicit source provenance and source-neutral encounter operations. */
    private static void migrateV9ToV10(CompoundTag tag) {
        for (Tag facilityElement : tag.getCompound("snapshot").getList("facilities", Tag.TAG_COMPOUND)) {
            CompoundTag facility = (CompoundTag) facilityElement;
            if (!facility.contains("infectionSource", Tag.TAG_STRING)) facility.putString("infectionSource", InfectionSourceId.CRIMSON.value());
        }
        for (Tag mineElement : tag.getList("testMines", Tag.TAG_COMPOUND)) {
            CompoundTag mine = (CompoundTag) mineElement;
            if (!mine.contains("job", Tag.TAG_COMPOUND)) continue;
            for (Tag operationElement : mine.getCompound("job").getList("operations", Tag.TAG_COMPOUND)) {
                CompoundTag operation = (CompoundTag) operationElement;
                operation.putString("type", switch (operation.getString("type")) {
                    case "ENSURE_CRIMSON_ENCOUNTER_ACTOR" -> "ENSURE_SOURCE_ENCOUNTER_ACTOR";
                    case "REMOVE_CRIMSON_ENCOUNTER_ACTOR" -> "REMOVE_SOURCE_ENCOUNTER_ACTOR";
                    default -> operation.getString("type");
                });
            }
        }
        tag.putInt("schemaVersion", 10);
    }

    /** v11 makes PM-owned combat health explicit for safe adapter combat. */
    private static void migrateV10ToV11(CompoundTag tag) {
        for (Tag mineElement : tag.getList("testMines", Tag.TAG_COMPOUND)) {
            CompoundTag mine = (CompoundTag) mineElement;
            if (!mine.contains("encounter", Tag.TAG_COMPOUND)) continue;
            for (Tag actorElement : mine.getCompound("encounter").getList("actors", Tag.TAG_COMPOUND)) {
                CompoundTag actor = (CompoundTag) actorElement;
                if (!actor.contains("combatHitPoints", Tag.TAG_INT)) {
                    actor.putInt("combatHitPoints", EncounterActorRef.UNINITIALIZED_COMBAT_HIT_POINTS);
                }
            }
        }
        tag.putInt("schemaVersion", 11);
    }

    /** v12 separates PM movement state from action/combat cooldowns. */
    private static void migrateV11ToV12(CompoundTag tag) {
        for (Tag mineElement : tag.getList("testMines", Tag.TAG_COMPOUND)) {
            CompoundTag mine = (CompoundTag) mineElement;
            if (!mine.contains("encounter", Tag.TAG_COMPOUND)) continue;
            for (Tag actorElement : mine.getCompound("encounter").getList("actors", Tag.TAG_COMPOUND)) {
                CompoundTag actor = (CompoundTag) actorElement;
                if (!actor.contains("nextMovementTick", Tag.TAG_LONG)) actor.putLong("nextMovementTick", 0L);
                if (!actor.contains("routeCursor", Tag.TAG_INT)) actor.putInt("routeCursor", 0);
            }
        }
        tag.putInt("schemaVersion", 12);
    }

    /** v13 pins an authored composition so a reload cannot reshuffle an active encounter. */
    private static void migrateV12ToV13(CompoundTag tag) {
        for (Tag mineElement : tag.getList("testMines", Tag.TAG_COMPOUND)) {
            CompoundTag mine = (CompoundTag) mineElement;
            if (mine.contains("encounter", Tag.TAG_COMPOUND) && !mine.getCompound("encounter").contains("composition", Tag.TAG_STRING)) {
                mine.getCompound("encounter").putString("composition", "");
            }
        }
        tag.putInt("schemaVersion", 13);
    }

    /** v14 adds a bounded persisted effect ledger; v13 has no in-flight effects to recover. */
    private static void migrateV13ToV14(CompoundTag tag) {
        if (!tag.contains("effectLeases", Tag.TAG_LIST)) tag.put("effectLeases", new ListTag());
        if (!tag.contains("quarantine", Tag.TAG_LIST)) tag.put("quarantine", new ListTag());
        tag.putInt("schemaVersion", 14);
    }

    /**
     * v15 intentionally does not adopt native health from a representation.
     * A legacy live actor is safely replaced from its desired PM slot on its
     * next materialization pass instead.
     */
    private static void migrateV14ToV15(CompoundTag tag) {
        if (!tag.contains("threatCombatActors", Tag.TAG_LIST)) tag.put("threatCombatActors", new ListTag());
        if (!tag.contains("pmProjectiles", Tag.TAG_LIST)) tag.put("pmProjectiles", new ListTag());
        tag.putInt("schemaVersion", 15);
    }
}
