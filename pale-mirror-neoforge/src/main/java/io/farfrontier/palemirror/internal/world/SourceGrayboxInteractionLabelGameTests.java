package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game-world proof that a player can read an exact interaction fact without retaining stale instructions. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxInteractionLabelGameTests {
    private SourceGrayboxInteractionLabelGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void interactionLabelExposesItsExactFactAndRetiresWhenTheFactClears(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 56);
        ReferenceGrayboxSnapshot snapshot = atArena(SourceGrayboxMaterializerGameTests.routeFixture(
                anchor, ReferenceGrayboxSimulation.create(42L).snapshot()), anchor);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        ReferenceGrayboxSnapshot.Interaction interaction = snapshot.interactions().getFirst();
        String text = "[X] route_damaged target=" + interaction.subjectId() + " total=18.00 available-slots="
                + interaction.slots().size() + " each=" + String.format(Locale.ROOT, "%.2f", interaction.totalWeight() / interaction.slots().size());
        AABB arena = new AABB(anchor).inflate(64, 64, 64);
        List<ArmorStand> labels = helper.getLevel().getEntitiesOfClass(ArmorStand.class, arena, value ->
                value.hasCustomName() && value.getCustomName().getString().equals(text));
        helper.assertTrue(labels.size() == 1,
                "every breakable source fact must name its type, exact target, remaining total and exact per-slot effect");

        materializer.apply(helper.getLevel(), withoutInteractions(snapshot));
        helper.assertTrue(labels.getFirst().isRemoved(),
                "a cleared source fact must discard its [X] label instead of leaving a stale player instruction");
        helper.succeed();
    }

    private static ReferenceGrayboxSnapshot withoutInteractions(ReferenceGrayboxSnapshot baseline) {
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                baseline.settlements(), baseline.facilities(), baseline.resourceSites(), baseline.routes(), baseline.hiveOrgans(), baseline.bioforms(),
                baseline.residents(), baseline.fieldPosts(), baseline.fieldLinks(), baseline.activities(), baseline.cargoes(), List.of(),
                baseline.sectors(), baseline.chrysalises(), baseline.readouts(), baseline.events());
    }

    private static ReferenceGrayboxSnapshot atArena(ReferenceGrayboxSnapshot baseline, BlockPos anchor) {
        ReferenceGrayboxLayout.Bounds bounds = new ReferenceGrayboxLayout.Bounds(anchor.getX() - 512, anchor.getZ() - 352,
                1_024, 704, ReferenceGrayboxLayout.GROUND_Y, ReferenceGrayboxLayout.BLOCKS_PER_CELL);
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), bounds, baseline.cells(),
                baseline.settlements(), baseline.facilities(), baseline.resourceSites(), baseline.routes(), baseline.hiveOrgans(), baseline.bioforms(),
                baseline.residents(), baseline.fieldPosts(), baseline.fieldLinks(), baseline.activities(), baseline.cargoes(), baseline.interactions(),
                baseline.sectors(), baseline.chrysalises(), baseline.readouts(), baseline.events());
    }
}
