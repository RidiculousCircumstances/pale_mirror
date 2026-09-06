package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live-world proof that co-located source records remain locally readable. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxLabelBoardGameTests {
    private SourceGrayboxLabelBoardGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void coLocatedObjectsReceiveSeparateLocalBoards(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 48);
        ReferenceGrayboxSnapshot snapshot = fixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot());
        new SourceGrayboxMaterializer().apply(helper.getLevel(), snapshot);

        AABB arena = new AABB(anchor).inflate(48, 32, 48);
        List<Display.TextDisplay> labels = helper.getLevel().getEntitiesOfClass(Display.TextDisplay.class, arena,
                value -> value.hasCustomName());
        Display.TextDisplay settlement = labels.stream().filter(value -> value.getCustomName().getString().startsWith("[SETTLEMENT] Testhold — STABLE"))
                .findFirst().orElseThrow(() -> new AssertionError("settlement board must be materialized"));
        Display.TextDisplay civicHall = labels.stream().filter(value -> value.getCustomName().getString().startsWith("[BUILDING] civic hall"))
                .findFirst().orElseThrow(() -> new AssertionError("co-located civic-hall board must be materialized"));

        helper.assertTrue(settlement.getX() != civicHall.getX() || settlement.getZ() != civicHall.getZ(),
                "co-located boards must separate sideways instead of stacking over the settlement");
        helper.assertTrue(Math.abs(settlement.getY() - civicHall.getY()) <= 2.0d,
                "co-located boards must stay near the local roof rather than becoming sky labels");
        helper.assertTrue(settlement.getY() <= anchor.getY() + 3,
                "a tall co-located landmark must make a board choose nearby low ground, never a sky position");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void currentEffectKeepsTheExactAnchorBoardAndRetiresWithItsSourceFact(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 48);
        ReferenceGrayboxSnapshot baseline = fixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot());
        ReferenceGrayboxSnapshot.Effect effect = new ReferenceGrayboxSnapshot.Effect("containment:board", "containment",
                "settlement:1", baseline.day(), new ReferenceGrayboxLayout.Point(anchor.getX(), anchor.getZ()), 2.0d, .5d,
                "tissue_removed=0.500", "effect.containment");
        ReferenceGrayboxSnapshot current = withEffects(baseline, List.of(effect));
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();

        materializer.apply(helper.getLevel(), current);
        String text = SourceGrayboxLiveBriefing.effectLabel(effect);
        List<Display.TextDisplay> labels = helper.getLevel().getEntitiesOfClass(Display.TextDisplay.class,
                new AABB(anchor).inflate(48, 32, 48), value -> value.hasCustomName() && value.getCustomName().getString().equals(text));
        helper.assertTrue(labels.size() == 1, "one current source effect must publish one player-facing board");
        Display.TextDisplay board = labels.getFirst();
        helper.assertValueEqual(board.blockPosition().getX(), anchor.getX(),
                "the current effect board must remain at the exact canonical effect X coordinate");
        helper.assertValueEqual(board.blockPosition().getZ(), anchor.getZ(),
                "the current effect board must remain at the exact canonical effect Z coordinate");
        helper.assertValueEqual(board.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getFloat("view_range"), 0.75f,
                "an effect board must remain local but readable before a player reaches the impact point");

        materializer.apply(helper.getLevel(), withEffects(baseline, List.of()));
        helper.assertTrue(board.isRemoved(), "a no-longer-current source effect must retire its board instead of leaving a false active scene");
        helper.succeed();
    }

    private static ReferenceGrayboxSnapshot fixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline) {
        ReferenceGrayboxLayout.Rectangle settlement = new ReferenceGrayboxLayout.Rectangle(anchor.getX() - 24, anchor.getZ() - 24, 48, 48);
        ReferenceGrayboxLayout.Rectangle civicHall = new ReferenceGrayboxLayout.Rectangle(anchor.getX() - 4, anchor.getZ() - 4, 8, 8);
        ReferenceGrayboxLayout.Bounds bounds = new ReferenceGrayboxLayout.Bounds(anchor.getX() - 512, anchor.getZ() - 352,
                1_024, 704, ReferenceGrayboxLayout.GROUND_Y, ReferenceGrayboxLayout.BLOCKS_PER_CELL);
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), bounds, baseline.cells(),
                List.of(new ReferenceGrayboxSnapshot.Settlement(1, "Testhold", 0, 0, settlement, true, 3.0d, 1.0d, 0.1d, 0.0d,
                        "stable", 5.0d, 1.0d, "settlement.stable")),
                List.of(new ReferenceGrayboxSnapshot.Facility("1:civic_hall", 1, "civic_hall", civicHall, 1.0d, "facility.civic_hall")),
                List.of(), List.of(), List.of(new ReferenceGrayboxSnapshot.HiveOrgan(7, "core", civicHall, 1.0d, 1.0d, false,
                "organ.core")), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    private static ReferenceGrayboxSnapshot withEffects(ReferenceGrayboxSnapshot baseline,
                                                         List<ReferenceGrayboxSnapshot.Effect> effects) {
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(),
                baseline.cells(), baseline.settlements(), baseline.facilities(), baseline.warehouses(), baseline.resourceSites(),
                baseline.routes(), baseline.hiveOrgans(), baseline.bioforms(), baseline.residents(), baseline.fieldPosts(),
                baseline.fieldLinks(), baseline.activities(), effects, baseline.cargoes(), baseline.interactions(), baseline.sectors(),
                baseline.chrysalises(), baseline.readouts(), baseline.events());
    }
}
