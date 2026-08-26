package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical admission proof: ambient mobs must not gain authority over source citizens. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxEntityAdmissionGameTests {
    private SourceGrayboxEntityAdmissionGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void sourceGrayboxRejectsForeignMobsButAcceptsAnExactProjectedCarrier(GameTestHelper helper) {
        Zombie foreignZombie = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        Villager foreignVillager = new Villager(EntityType.VILLAGER, helper.getLevel());
        helper.assertTrue(SourceGrayboxEntityAdmission.rejects(SourceGrayboxWorldBoundary.DIMENSION, foreignZombie),
                "a native or modded hostile must not enter the source-graybox and create off-model casualties");
        helper.assertTrue(SourceGrayboxEntityAdmission.rejects(SourceGrayboxWorldBoundary.DIMENSION, foreignVillager),
                "a player-spawned or ambient Villager must not impersonate a source resident");
        helper.assertTrue(!SourceGrayboxEntityAdmission.rejects(helper.getLevel().dimension(), foreignZombie),
                "the graybox admission boundary must not change ordinary dimensions");

        String residentId = ReferenceGrayboxSimulation.create(42L).snapshot().residents().getFirst().id();
        Villager projectedCarrier = new Villager(EntityType.VILLAGER, helper.getLevel());
        projectedCarrier.setUUID(SourceGrayboxMaterializer.uuid("resident", residentId));
        projectedCarrier.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_ID, residentId);
        projectedCarrier.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_KIND, "RESIDENT");
        projectedCarrier.getPersistentData().putString(SourceGrayboxMaterializer.ENTITY_REVISION,
                "0000000000000000000000000000000000000000000000000000000000000000");
        helper.assertTrue(!SourceGrayboxEntityAdmission.rejects(SourceGrayboxWorldBoundary.DIMENSION, projectedCarrier),
                "the exact deterministic carrier must be admitted so source materialization remains complete");
        helper.succeed();
    }
}
