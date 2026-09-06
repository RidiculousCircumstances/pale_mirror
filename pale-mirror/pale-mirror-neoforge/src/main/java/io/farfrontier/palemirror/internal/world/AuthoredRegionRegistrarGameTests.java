package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for durable authored-place identity before its chunks are loaded. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AuthoredRegionRegistrarGameTests {
    private AuthoredRegionRegistrarGameTests() { }

    @GameTest(batch = "pm-authored-place-registry", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void immutableGenesisIdentityRegistersAndReusesPhysicalPlace(GameTestHelper helper) {
        WorldObjectRegistry registry = new WorldObjectRegistry();
        WorldObjectId place = new WorldObjectId("pale_mirror:authored_test_place");
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos minimum = anchor.offset(-45, -8, -65);
        BlockPos maximum = anchor.offset(45, 40, 65);

        helper.assertTrue(AuthoredRegionRegistrar.ensureAuthoredPlace(registry, place, "minecraft:overworld",
                anchor, minimum, maximum, "10:content-hash"),
                "the immutable genesis identity must create the authored physical place");
        WorldObjectRegistryEntry entry = registry.require(place);
        helper.assertValueEqual(entry.dimensionId(), "minecraft:overworld", "dimension must survive registration");
        helper.assertValueEqual(entry.anchor(), anchor, "anchor must survive registration");
        helper.assertValueEqual(entry.minBounds(), minimum, "minimum bounds must survive registration");
        helper.assertValueEqual(entry.maxBounds(), maximum, "maximum bounds must survive registration");
        helper.assertValueEqual(entry.templateId(), "pale_mirror:authored_settlement",
                "authored places require a distinct physical template identity");
        helper.assertValueEqual(entry.templateVersion(), "10:content-hash",
                "manifest version and hash must guard physical identity");
        helper.assertValueEqual(entry.lifecycle(), WorldObjectLifecycle.REPRESENTED,
                "a genesis-planned settlement is represented before its chunks are loaded");
        helper.assertTrue(!AuthoredRegionRegistrar.ensureAuthoredPlace(registry, place, "minecraft:overworld",
                        anchor, minimum, maximum, "10:content-hash"),
                "reconciliation must reuse an identical persisted identity");
        helper.assertValueEqual(registry.entries().size(), 1, "reconciliation must not duplicate the place");

        try {
            AuthoredRegionRegistrar.ensureAuthoredPlace(registry, place, "minecraft:overworld",
                    anchor.offset(1, 0, 0), minimum, maximum, "10:content-hash");
            helper.fail("a conflicting authored physical identity must fail closed");
        } catch (IllegalStateException expected) {
            helper.assertTrue(expected.getMessage().contains(place.value()),
                    "the conflict diagnostic must name the authored place");
        }
        helper.succeed();
    }
}
