package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical proof for the source snapshot boundary and its conflict recovery path. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxMaterializerGameTests {
    private SourceGrayboxMaterializerGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void sourceSnapshotCreatesExactActorsAndRetainsAnUnsupportedBlockConflict(GameTestHelper helper) {
        BlockPos testOrigin = helper.absolutePos(BlockPos.ZERO);
        BlockPos anchor = new BlockPos(testOrigin.getX(), ReferenceGrayboxLayout.GROUND_Y, testOrigin.getZ());
        prepareFlatFloor(helper, anchor, 18);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        String residentId = baseline.residents().getFirst().id();
        ReferenceGrayboxSnapshot snapshot = fixture(anchor, baseline, residentId, 1.0d, "fixture");
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();

        SourceGrayboxMaterializer.Report first = materializer.apply(helper.getLevel(), snapshot);
        BlockPos facility = anchor.offset(2, 0, 2);
        helper.assertValueEqual(helper.getLevel().getBlockState(facility).getBlock(), Blocks.BLUE_WOOL,
                "a source facility must become its readable colour-coded rectangle");
        Villager resident = helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(16), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals(residentId)).stream().findFirst().orElseThrow();
        Zombie bioform = helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(anchor).inflate(16), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals("bioform:1:1")).stream().findFirst().orElseThrow();
        helper.assertValueEqual(resident.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_REVISION), snapshot.stateRevision(),
                "one materialized Villager must retain the source revision that makes a death fact checkable");
        helper.assertValueEqual(bioform.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_KIND), "BIOFORM",
                "one source bioform must become exactly one managed Zombie");
        helper.assertTrue(first.placed() > 0, "the loaded part of a valid snapshot must create source-owned geometry");
        SourceGrayboxMaterializer.ManagedEntity physicalResident = SourceGrayboxMaterializer.managed(resident);
        helper.assertTrue(SourceGrayboxSavedData.fresh(42L).observe(
                        io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation.killed(
                                "gametest:materialized-resident", physicalResident.revision(), physicalResident.id())).applied(),
                "a managed Villager's identity and revision must become an accepted exact source fact");

        materializer.recordBlockConflict(helper.getLevel(), facility);
        helper.getLevel().setBlock(facility, Blocks.AIR.defaultBlockState(), 3);
        materializer.apply(helper.getLevel(), snapshot);
        helper.assertValueEqual(helper.getLevel().getBlockState(facility).getBlock(), Blocks.AIR,
                "an unsupported structural perturbation must remain visibly conflicted, never be silently repaired");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void presentationClaimSurvivesRestartEncodingAndKeepsItsConflict(GameTestHelper helper) {
        SourceGrayboxPresentationLedger ledger = SourceGrayboxPresentationLedger.get(helper.getLevel());
        SourceGrayboxPresentationLedger.Claim claim = new SourceGrayboxPresentationLedger.Claim("fixture:claim", "fixture", "FACILITY",
                "a".repeat(64), 1, 64, 1, 2, 2, 1, false, "", 0.0d, false);
        ledger.put(claim);
        ledger.conflict(claim.id());

        SourceGrayboxPresentationLedger restored = SourceGrayboxPresentationLedger.load(
                ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);

        helper.assertTrue(restored.claim(claim.id()) != null && restored.claim(claim.id()).conflicted(),
                "a source-graybox structural conflict must survive persistence instead of being silently repaired after restart");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void interactionSlotCarriesAnExactRemainingShareAndStaysConsumed(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        prepareFlatFloor(helper, anchor, 18);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        String residentId = baseline.residents().getFirst().id();
        ReferenceGrayboxSnapshot first = fixture(anchor, baseline, residentId, 1.0d, "exact-share");
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), first);
        ReferenceGrayboxLayout.Point slot = first.interactions().getFirst().slots().getFirst();
        BlockPos broken = new BlockPos(slot.x(), ReferenceGrayboxLayout.GROUND_Y + 1, slot.z());
        SourceGrayboxPresentationLedger.Claim original = materializer.claimAt(helper.getLevel(), broken);
        helper.assertValueEqual(original.interactionKind(), "facility_damaged", "a visible slot must retain its declared source fact kind");
        helper.assertValueEqual(original.interactionWeight(), 0.25d, "four intact slots divide the source facility exactly");

        materializer.consumeBlockClaim(helper.getLevel(), broken);
        helper.getLevel().setBlock(broken, Blocks.AIR.defaultBlockState(), 3);
        ReferenceGrayboxSnapshot afterOneBreak = fixture(anchor, baseline, residentId, 0.75d, "exact-share");
        materializer.apply(helper.getLevel(), afterOneBreak);

        SourceGrayboxPresentationLedger.Claim consumed = materializer.claimAt(helper.getLevel(), broken);
        ReferenceGrayboxLayout.Point nextSlot = afterOneBreak.interactions().getFirst().slots().get(1);
        SourceGrayboxPresentationLedger.Claim remaining = materializer.claimAt(helper.getLevel(),
                new BlockPos(nextSlot.x(), ReferenceGrayboxLayout.GROUND_Y + 1, nextSlot.z()));
        helper.assertTrue(consumed.consumed(), "an accepted interaction slot must not be recreated by a later materialization pass");
        helper.assertValueEqual(remaining.interactionWeight(), 0.25d,
                "the remaining source weight must be redistributed only among remaining physical slots");
        helper.assertValueEqual(helper.getLevel().getBlockState(broken).getBlock(), Blocks.AIR,
                "a consumed interaction remains visibly broken");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void fieldPostPalletCarriesItsOwnTypedCargoFact(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        prepareFlatFloor(helper, anchor, 18);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot snapshot = fieldPostCargoFixture(anchor, baseline, 701);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        ReferenceGrayboxSnapshot.Interaction interaction = snapshot.interactions().stream()
                .filter(value -> value.kind().equals("field_post_cargo_lost")).findFirst().orElseThrow();
        ReferenceGrayboxLayout.Point slot = interaction.slots().getFirst();
        SourceGrayboxPresentationLedger.Claim claim = materializer.claimAt(helper.getLevel(),
                new BlockPos(slot.x(), ReferenceGrayboxLayout.GROUND_Y + interaction.yOffset(), slot.z()));
        helper.assertValueEqual(claim.subjectId(), "field_post:701:cargo:food",
                "one field-post pallet must retain its exact canonical stock subject");
        helper.assertValueEqual(claim.interactionKind(), "field_post_cargo_lost",
                "a field-post pallet must not be mistaken for travelling operation cargo");
        helper.assertValueEqual(claim.interactionWeight(), 1.7d,
                "the one-slot post pallet must carry its full source stock quantity");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void fieldPostStructureCarriesItsOwnDamageFact(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        prepareFlatFloor(helper, anchor, 18);
        ReferenceGrayboxSnapshot snapshot = fieldPostCargoFixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot(), 702);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        ReferenceGrayboxSnapshot.Interaction interaction = snapshot.interactions().stream()
                .filter(value -> value.kind().equals("field_post_damaged")).findFirst().orElseThrow();
        ReferenceGrayboxLayout.Point slot = interaction.slots().getFirst();
        SourceGrayboxPresentationLedger.Claim claim = materializer.claimAt(helper.getLevel(),
                new BlockPos(slot.x(), ReferenceGrayboxLayout.GROUND_Y + interaction.yOffset(), slot.z()));
        helper.assertValueEqual(claim.subjectId(), "field_post:702",
                "the field-post structure must retain its own source identity");
        helper.assertValueEqual(claim.interactionKind(), "field_post_damaged",
                "structural damage must remain distinct from stock loss");
        helper.assertValueEqual(claim.interactionWeight(), 10.0d / 16.0d,
                "sixteen intact structural slots divide post integrity exactly");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void fieldLinkCarriesReadableGeometryAndItsOwnDamageFact(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        prepareFlatFloor(helper, anchor, 44);
        ReferenceGrayboxSnapshot snapshot = fieldLinkFixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot(), 703);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        ReferenceGrayboxSnapshot.FieldLink link = snapshot.fieldLinks().getFirst();
        ReferenceGrayboxLayout.Point slot = link.slots().getFirst();
        SourceGrayboxPresentationLedger.Claim structure = materializer.claimAt(helper.getLevel(),
                new BlockPos(slot.x(), ReferenceGrayboxLayout.GROUND_Y, slot.z()));
        helper.assertValueEqual(structure.subjectId(), "field_link:703",
                "a field link must be a visible managed graybox line, not only a label");
        helper.assertValueEqual(structure.kind(), "FIELD_LINK", "the line geometry must retain its semantic owner");
        ReferenceGrayboxSnapshot.Interaction interaction = snapshot.interactions().stream()
                .filter(value -> value.kind().equals("field_link_damaged")).findFirst().orElseThrow();
        SourceGrayboxPresentationLedger.Claim damage = materializer.claimAt(helper.getLevel(),
                new BlockPos(slot.x(), ReferenceGrayboxLayout.GROUND_Y + interaction.yOffset(), slot.z()));
        helper.assertValueEqual(damage.subjectId(), "field_link:703", "the line damage slot must retain its exact source identity");
        helper.assertValueEqual(damage.interactionWeight(), 12.0d / link.slots().size(),
                "visible line slots must divide integrity without a hidden multiplier");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void fortificationIsAPerimeterAndDoesNotPaintOverFunctionalBuildings(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        prepareFlatFloor(helper, anchor, 56);
        ReferenceGrayboxSnapshot snapshot = settlementFixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot());
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        ReferenceGrayboxSnapshot.Facility workshop = snapshot.facilities().stream()
                .filter(value -> value.kind().equals("workshop")).findFirst().orElseThrow();
        ReferenceGrayboxSnapshot.Facility fortification = snapshot.facilities().stream()
                .filter(value -> value.kind().equals("fortification")).findFirst().orElseThrow();
        BlockPos workshopBlock = new BlockPos(workshop.rectangle().centreX(), ReferenceGrayboxLayout.GROUND_Y, workshop.rectangle().centreZ());
        BlockPos wallBlock = new BlockPos(fortification.rectangle().x(), ReferenceGrayboxLayout.GROUND_Y, fortification.rectangle().z());
        helper.assertValueEqual(helper.getLevel().getBlockState(workshopBlock).getBlock(), Blocks.BLUE_WOOL,
                "a functional workshop must retain its own readable colour inside a fortified settlement");
        helper.assertValueEqual(helper.getLevel().getBlockState(wallBlock).getBlock(), Blocks.CYAN_WOOL,
                "fortification must be a visible perimeter rather than a filled overlay");
        helper.assertValueEqual(materializer.claimAt(helper.getLevel(), workshopBlock).subjectId(), workshop.id(),
                "a building's physical block must resolve to the functional facility, not to fortification");
        helper.assertValueEqual(materializer.claimAt(helper.getLevel(), wallBlock).subjectId(), fortification.id(),
                "a perimeter block must retain the fortification source identity");
        helper.succeed();
    }

    private static void prepareFlatFloor(GameTestHelper helper, BlockPos anchor, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            helper.getLevel().setBlock(anchor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y <= 5; y++) helper.getLevel().setBlock(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static ReferenceGrayboxSnapshot fixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline, String residentId, double interactionWeight,
                                                     String fixtureId) {
        ReferenceGrayboxLayout.Rectangle facility = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 2, anchor.getZ() + 2, 4, 4);
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(new ReferenceGrayboxSnapshot.Facility(fixtureId + ":workshop", 1, "workshop", facility, 1.0d,
                "facility.workshop")), List.of(), List.of(), List.of(),
                List.of(new ReferenceGrayboxSnapshot.Bioform("bioform:1:1", 1, "harvester",
                        new ReferenceGrayboxLayout.Point(anchor.getX() + 10, anchor.getZ() + 4), "engaging", false, "bioform.harvester")),
                List.of(new ReferenceGrayboxSnapshot.Resident(residentId, 1, "guard", "worker", "settlement", 1, "healthy", null,
                        new ReferenceGrayboxLayout.Point(anchor.getX() + 8, anchor.getZ() + 4), "resident.guard")),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new ReferenceGrayboxSnapshot.Interaction(fixtureId + ":workshop", "settlement:1:facility:workshop", "facility_damaged",
                        interactionWeight, 1, ReferenceGrayboxLayout.interactionSlots(facility, 4), "facility.workshop")),
                List.of(), List.of(), List.of());
    }

    private static ReferenceGrayboxSnapshot fieldPostCargoFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline, int postId) {
        ReferenceGrayboxLayout.Rectangle post = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 2, anchor.getZ() + 2, 12, 12);
        ReferenceGrayboxLayout.Rectangle pallet = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 1, anchor.getZ(), 1, 1);
        ReferenceGrayboxSnapshot.Cargo cargo = new ReferenceGrayboxSnapshot.Cargo("field_post:" + postId + ":cargo:food", "field_post", postId,
                "food", 1.7d, pallet, "cargo.food");
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ReferenceGrayboxSnapshot.FieldPost(postId, 1, "checkpoint", "active", post, 10.0d, 1, 0,
                        List.of(), "post.checkpoint")), List.of(), List.of(), List.of(cargo),
                List.of(new ReferenceGrayboxSnapshot.Interaction("field-post-cargo:" + postId, cargo.id(), "field_post_cargo_lost", 1.7d, 1,
                        ReferenceGrayboxLayout.interactionSlots(pallet, 1), cargo.colour()),
                        new ReferenceGrayboxSnapshot.Interaction("field-post:" + postId, "field_post:" + postId, "field_post_damaged", 10.0d, 4,
                                ReferenceGrayboxLayout.interactionSlots(post, 16), "post.checkpoint")),
                List.of(), List.of(), List.of());
    }

    private static ReferenceGrayboxSnapshot fieldLinkFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline, int linkId) {
        ReferenceGrayboxLayout.Rectangle first = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 2, anchor.getZ() + 2, 12, 12);
        ReferenceGrayboxLayout.Rectangle second = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 26, anchor.getZ() + 2, 12, 12);
        List<ReferenceGrayboxLayout.Point> slots = ReferenceGrayboxLayout.fieldLinkSlots(
                new ReferenceGrayboxLayout.Point(first.centreX(), first.centreZ()),
                new ReferenceGrayboxLayout.Point(second.centreX(), second.centreZ()));
        ReferenceGrayboxSnapshot.FieldLink link = new ReferenceGrayboxSnapshot.FieldLink(linkId, 1, "supply_corridor", 101, 102,
                "active", 12.0d, slots, "link.supply_corridor.active");
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ReferenceGrayboxSnapshot.FieldPost(101, 1, "checkpoint", "active", first, 10.0d, 1, 0,
                                List.of(), "post.checkpoint"),
                        new ReferenceGrayboxSnapshot.FieldPost(102, 1, "checkpoint", "active", second, 10.0d, 1, 0,
                                List.of(), "post.checkpoint")),
                List.of(link), List.of(), List.of(),
                List.of(new ReferenceGrayboxSnapshot.Interaction("field-link:" + linkId, "field_link:" + linkId, "field_link_damaged",
                        link.integrity(), 2, slots, link.colour())),
                List.of(), List.of(), List.of());
    }

    private static ReferenceGrayboxSnapshot settlementFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline) {
        ReferenceGrayboxLayout.Rectangle settlement = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 2, anchor.getZ() + 2, 48, 48);
        ReferenceGrayboxLayout.Rectangle workshop = ReferenceGrayboxLayout.facility(settlement, "workshop");
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(new ReferenceGrayboxSnapshot.Facility("fixture:settlement:workshop", 1, "workshop", workshop, 1.0d,
                                "facility.workshop"),
                        new ReferenceGrayboxSnapshot.Facility("fixture:settlement:fortification", 1, "fortification", settlement, 1.0d,
                                "facility.fortification")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
