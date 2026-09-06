package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Durable migration proof for external-explosion post-impact reconciliation. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxExplosionStateGameTests {
    private SourceGrayboxExplosionStateGameTests() { }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void v28MigrationStartsWithAnEmptyDurableExternalExplosionLedger(GameTestHelper helper) {
        SourceGrayboxSavedData source = SourceGrayboxSavedData.fresh(42L);
        CompoundTag legacy = source.save(new CompoundTag(), null);
        legacy.putInt("schemaVersion", 28);
        legacy.remove("pendingExplosions");
        legacy.remove("nextExplosionSequence");

        SourceGrayboxSavedData restored = SourceGrayboxSavedData.load(legacy, null);
        CompoundTag upgraded = restored.save(new CompoundTag(), null);
        helper.assertValueEqual(upgraded.getInt("schemaVersion"), 29,
                "a v28 source document must be durably upgraded before external explosions can be observed");
        helper.assertTrue(upgraded.contains("pendingExplosions", Tag.TAG_LIST)
                        && upgraded.contains("nextExplosionSequence", Tag.TAG_LONG),
                "the v29 envelope must not guess explosion recovery state after a restart");
        helper.assertTrue(restored.isDirty(), "the v28 migration must request a save even when no external blast occurs later");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-state", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void currentSchemaRejectsAMissingExternalExplosionLedger(GameTestHelper helper) {
        CompoundTag corrupt = SourceGrayboxSavedData.fresh(42L).save(new CompoundTag(), null);
        corrupt.remove("pendingExplosions");
        boolean rejected = false;
        try {
            SourceGrayboxSavedData.load(corrupt, null);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "a current world with an omitted pending external blast must fail closed, not forget an unreconciled physical cause");
        helper.succeed();
    }
}
