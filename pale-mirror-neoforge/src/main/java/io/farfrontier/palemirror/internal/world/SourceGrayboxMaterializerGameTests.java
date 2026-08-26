package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
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
        ReferenceGrayboxSnapshot snapshot = withReadout(fixture(anchor, baseline, residentId, 1.0d, "fixture"),
                new ReferenceGrayboxSnapshot.Readout("fixture:market", "MARKET", "cash=12.00 stock=food=4.00",
                        new ReferenceGrayboxLayout.Point(anchor.getX() + 6, anchor.getZ() + 6), "readout.market"));
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();

        SourceGrayboxMaterializer.Report first = materializer.apply(helper.getLevel(), snapshot);
        BlockPos facility = anchor.offset(2, 0, 2);
        helper.assertValueEqual(helper.getLevel().getBlockState(facility).getBlock(), Blocks.BLUE_WOOL,
                "a source facility must become its readable colour-coded rectangle");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(anchor.offset(6, 6, 6)).inflate(4, 32, 4), value ->
                value.hasCustomName() && value.getCustomName().getString().startsWith("[MARKET] cash=12.00")).isEmpty(),
                "dense source dashboards must not turn every row into an overlapping map nameplate");
        ArmorStand facilityLabel = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(facility).inflate(16, 32, 16), value ->
                value.hasCustomName() && value.getCustomName().getString().startsWith("[F] workshop")).stream().findFirst().orElseThrow();
        helper.assertTrue(facilityLabel.getY() >= ReferenceGrayboxLayout.GROUND_Y + 17,
                "a readable source label must be above the compact presentation stack, not embedded in its blocks");
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
    public static void tradeRouteCarriesGroundGeometryAndAnElevatedDamageFact(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        prepareFlatFloor(helper, anchor, 56);
        ReferenceGrayboxSnapshot snapshot = routeFixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot());
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        ReferenceGrayboxSnapshot.Route route = snapshot.routes().getFirst();
        ReferenceGrayboxLayout.Point segment = ReferenceGrayboxLayout.routeSlots(route.start(), route.end()).getFirst();
        BlockPos ground = new BlockPos(segment.x(), ReferenceGrayboxLayout.GROUND_Y, segment.z());
        SourceGrayboxPresentationLedger.Claim routeClaim = materializer.claimAt(helper.getLevel(), ground);
        helper.assertValueEqual(helper.getLevel().getBlockState(ground).getBlock(), Blocks.BLUE_WOOL,
                "an open trade route must have readable logistics geometry on the graybox ground");
        helper.assertValueEqual(routeClaim.kind(), "ROUTE", "the ground segment must retain its route semantic kind");
        helper.assertValueEqual(routeClaim.subjectId(), route.id(), "the ground segment must retain its exact source route ID");

        ReferenceGrayboxSnapshot.Interaction interaction = snapshot.interactions().getFirst();
        SourceGrayboxPresentationLedger.Claim damage = materializer.claimAt(helper.getLevel(),
                new BlockPos(segment.x(), ReferenceGrayboxLayout.GROUND_Y + interaction.yOffset(), segment.z()));
        helper.assertValueEqual(damage.subjectId(), route.id(), "an elevated route slot must target that exact route, not its marker");
        helper.assertValueEqual(damage.interactionKind(), "route_damaged", "route damage must remain a typed source fact");
        helper.assertValueEqual(damage.interactionWeight(), 18.0d / interaction.slots().size(),
                "route capacity must be divided across visible slots without a hidden multiplier");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeRaidKeepsItsMarkerLabelAndOneToOneParticipantsLegible(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot snapshot = activeRaidFixture(anchor, ReferenceGrayboxSimulation.create(42L).snapshot());
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        materializer.apply(helper.getLevel(), snapshot);

        ReferenceGrayboxSnapshot.Activity raid = snapshot.activities().getFirst();
        BlockPos marker = new BlockPos(raid.position().x(), ReferenceGrayboxLayout.GROUND_Y + 6, raid.position().z());
        SourceGrayboxPresentationLedger.Claim claim = materializer.claimAt(helper.getLevel(), marker);
        helper.assertValueEqual(helper.getLevel().getBlockState(marker).getBlock(), Blocks.RED_WOOL,
                "an engaging raid must project a red operation marker instead of an ambiguous generic block");
        helper.assertValueEqual(claim.subjectId(), raid.id(), "the raid marker must retain its exact source activity ID");
        helper.assertValueEqual(claim.kind(), "ACTIVITY", "the raid marker must retain its operation semantic kind");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(marker).inflate(3, 24, 3), value ->
                        value.hasCustomName() && value.getCustomName().getString().equals("[A] operation#47 raid engaging p=2.00 i=0.50")).size() == 1,
                "an active raid must expose its family, kind, phase, personnel and risk in a readable label");

        Villager guard = helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(24), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals("resident:raid:guard")).stream().findFirst().orElseThrow();
        Villager engineer = helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(24), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals("resident:raid:engineer")).stream().findFirst().orElseThrow();
        helper.assertValueEqual(guard.getItemBySlot(EquipmentSlot.HEAD).getItem(), Items.RED_WOOL,
                "one deployed guard must remain one red-hatted Villager, not an aggregated operation counter");
        helper.assertValueEqual(engineer.getItemBySlot(EquipmentSlot.HEAD).getItem(), Items.YELLOW_WOOL,
                "a deployed engineer must remain visibly distinct while still being one exact Villager");

        Zombie raider = helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(anchor).inflate(24), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals("bioform:raid:raider")).stream().findFirst().orElseThrow();
        Zombie breaker = helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(anchor).inflate(24), value ->
                value.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID).equals("bioform:raid:breaker")).stream().findFirst().orElseThrow();
        helper.assertValueEqual(raider.getItemBySlot(EquipmentSlot.HEAD).getItem(), Items.RED_WOOL,
                "one attacking raider must remain one red-hatted Zombie");
        helper.assertValueEqual(breaker.getItemBySlot(EquipmentSlot.HEAD).getItem(), Items.ORANGE_WOOL,
                "one attacking breaker must remain one orange-hatted Zombie");
        helper.assertTrue(raider.getCustomName().getString().equals("raider | engaging") && raider.isCustomNameVisible(),
                "the hostile actor must expose its exact type and phase to a manual graybox reader");
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

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void paletteSeparatesTheSourceStatesThatDriveManualGrayboxReading(GameTestHelper helper) {
        helper.assertValueEqual(SourceGrayboxPalette.block("cell.feral_active").getBlock(), Blocks.RED_WOOL,
                "an active feral cell must not be visually indistinguishable from a neutral cell");
        helper.assertValueEqual(SourceGrayboxPalette.block("cell.signal_active").getBlock(), Blocks.PURPLE_WOOL,
                "a signal-led hive cell must remain visibly different from feral infection");
        helper.assertValueEqual(SourceGrayboxPalette.block("route.disrupted").getBlock(), Blocks.RED_WOOL,
                "a disrupted route must be distinguishable from an open blue route");
        helper.assertValueEqual(SourceGrayboxPalette.block("route.open").getBlock(), Blocks.BLUE_WOOL,
                "an open route must retain the logistics colour");
        helper.assertValueEqual(SourceGrayboxPalette.block("cargo.medicine").getBlock(), Blocks.WHITE_WOOL,
                "medical cargo must remain visibly separate from food and ammunition");
        helper.assertValueEqual(SourceGrayboxPalette.block("cargo.ammo").getBlock(), Blocks.RED_WOOL,
                "ammunition cargo must retain its combat colour");
        helper.assertValueEqual(SourceGrayboxPalette.block("post.observation_post").getBlock(), Blocks.YELLOW_WOOL,
                "an observation post must be distinguishable from a checkpoint or a strongpoint");
        helper.assertValueEqual(SourceGrayboxPalette.block("post.strongpoint").getBlock(), Blocks.RED_WOOL,
                "a strongpoint must retain its high-threat defensive colour");
        helper.assertValueEqual(SourceGrayboxPalette.block("sector.human").getBlock(), Blocks.CYAN_WOOL,
                "human territorial control must not collapse into the neutral sector colour");
        helper.assertValueEqual(SourceGrayboxPalette.block("activity.operation.engaging").getBlock(), Blocks.RED_WOOL,
                "an engaging operation must retain its combat colour rather than becoming a neutral marker");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void coLocatedSourceFactsUseDistinctPhysicalLayers(GameTestHelper helper) {
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot.Cell cell = baseline.cells().getFirst();
        ReferenceGrayboxSnapshot coLocated = new ReferenceGrayboxSnapshot(
                baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(), List.of(), List.of(),
                List.of(new ReferenceGrayboxSnapshot.ResourceSite(1, "mine", -1,
                        new ReferenceGrayboxLayout.Rectangle(cell.rectangle().x() + 2, cell.rectangle().z() + 2, 12, 12),
                        1.0d, 1.0d, 0.0d, "site.mine")),
                List.of(), List.of(new ReferenceGrayboxSnapshot.HiveOrgan(1, "core",
                        new ReferenceGrayboxLayout.Rectangle(cell.rectangle().x() + 3, cell.rectangle().z() + 3, 10, 10),
                        1.0d, 1.0d, false, "organ.core")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var desired = SourceGrayboxPresentationPlan.from(coLocated);
        SourceGrayboxPresentationPlan.Desired site = desired.get("resource-site:1");
        SourceGrayboxPresentationPlan.Desired organ = desired.get("hive-organ:1");
        helper.assertTrue(SourceGrayboxPresentationPlan.positions(site).stream().noneMatch(SourceGrayboxPresentationPlan.positions(organ)::contains),
                "co-located source facts must remain two physical objects rather than hiding one another");
        helper.assertTrue(organ.y() > site.y(), "a co-located hive organ must receive a deterministic layer above its resource site");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void newlyCoLocatedFactRelocatesOnlyThePriorManagedClaim(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        prepareFlatFloor(helper, anchor, 18);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
        ReferenceGrayboxSnapshot organOnly = coLocatedFixture(anchor, baseline, false);
        ReferenceGrayboxSnapshot coLocated = coLocatedFixture(anchor, baseline, true);
        ReferenceGrayboxLayout.Rectangle organ = organOnly.hiveOrgans().getFirst().rectangle();
        BlockPos probe = new BlockPos(organ.centreX(), ReferenceGrayboxLayout.GROUND_Y, organ.centreZ());

        materializer.apply(helper.getLevel(), organOnly);
        helper.assertValueEqual(helper.getLevel().getBlockState(probe).getBlock(), Blocks.RED_WOOL,
                "an isolated core begins at the source ground layer");
        materializer.apply(helper.getLevel(), coLocated);

        helper.assertValueEqual(helper.getLevel().getBlockState(probe).getBlock(), Blocks.ORANGE_WOOL,
                "a newly co-located resource site replaces only the old PM-owned lower core layer");
        helper.assertValueEqual(helper.getLevel().getBlockState(probe.above()).getBlock(), Blocks.RED_WOOL,
                "the core is retained on its deterministic layer rather than being silently dropped");
        helper.assertValueEqual(materializer.claimAt(helper.getLevel(), probe).subjectId(), "site:1",
                "the lower layer must belong to the new exact resource-site subject");
        helper.assertValueEqual(materializer.claimAt(helper.getLevel(), probe.above()).subjectId(), "organ:1",
                "the raised layer must retain the original exact hive-organ subject");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void duplicateCanonicalClaimsFailBeforeAnyMinecraftWrite(GameTestHelper helper) {
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot.Cell cell = baseline.cells().getFirst();
        ReferenceGrayboxSnapshot.Cargo duplicate = new ReferenceGrayboxSnapshot.Cargo("fixture:duplicate", "operation", 1, "food", 1.0d,
                new ReferenceGrayboxLayout.Rectangle(cell.rectangle().x() + 2, cell.rectangle().z() + 2, 1, 1), "cargo.food");
        ReferenceGrayboxSnapshot malformed = new ReferenceGrayboxSnapshot(
                baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(), baseline.settlements(),
                baseline.facilities(),
                baseline.resourceSites(), baseline.routes(), baseline.hiveOrgans(), baseline.bioforms(), baseline.residents(),
                baseline.fieldPosts(), baseline.fieldLinks(), baseline.activities(), List.of(duplicate, duplicate), baseline.interactions(),
                baseline.sectors(), baseline.chrysalises(), baseline.events());

        try {
            SourceGrayboxMaterializer.validateProjection(malformed);
            helper.fail("source graybox must reject duplicate canonical claims before materialization");
        } catch (IllegalStateException expected) {
            helper.succeed();
        }
    }

    static void prepareFlatFloor(GameTestHelper helper, BlockPos anchor, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            helper.getLevel().setBlock(anchor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y <= 5; y++) helper.getLevel().setBlock(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    static ReferenceGrayboxSnapshot fixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline, String residentId, double interactionWeight,
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

    private static ReferenceGrayboxSnapshot withReadout(ReferenceGrayboxSnapshot baseline, ReferenceGrayboxSnapshot.Readout readout) {
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                baseline.settlements(), baseline.facilities(), baseline.resourceSites(), baseline.routes(), baseline.hiveOrgans(), baseline.bioforms(),
                baseline.residents(), baseline.fieldPosts(), baseline.fieldLinks(), baseline.activities(), baseline.cargoes(), baseline.interactions(),
                baseline.sectors(), baseline.chrysalises(), List.of(readout), baseline.events());
    }

    static ReferenceGrayboxSnapshot withoutPresentationRecords(ReferenceGrayboxSnapshot baseline) {
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static ReferenceGrayboxSnapshot coLocatedFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline, boolean includeSite) {
        ReferenceGrayboxLayout.Rectangle site = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 2, anchor.getZ() + 2, 12, 12);
        ReferenceGrayboxLayout.Rectangle organ = new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 3, anchor.getZ() + 3, 10, 10);
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(), includeSite ? List.of(new ReferenceGrayboxSnapshot.ResourceSite(1, "mine", -1,
                site, 1.0d, 1.0d, 0.0d, "site.mine")) : List.of(), List.of(),
                List.of(new ReferenceGrayboxSnapshot.HiveOrgan(1, "core", organ, 1.0d, 1.0d, false, "organ.core")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
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

    static ReferenceGrayboxSnapshot routeFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline) {
        ReferenceGrayboxLayout.Point start = new ReferenceGrayboxLayout.Point(anchor.getX() + 4, anchor.getZ() + 4);
        ReferenceGrayboxLayout.Point end = new ReferenceGrayboxLayout.Point(anchor.getX() + 44, anchor.getZ() + 4);
        ReferenceGrayboxSnapshot.Route route = new ReferenceGrayboxSnapshot.Route("route:701:702", 701, 702, start, end,
                18.0d, 0.2d, 0.1d, false, false, "route.open");
        List<ReferenceGrayboxLayout.Point> slots = ReferenceGrayboxLayout.routeSlots(start, end);
        ReferenceGrayboxSnapshot.Interaction interaction = new ReferenceGrayboxSnapshot.Interaction("route:" + route.id(), route.id(),
                "route_damaged", route.capacity(), 3, slots, route.colour());
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(), List.of(), List.of(route), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(interaction), List.of(), List.of(), List.of());
    }

    private static ReferenceGrayboxSnapshot activeRaidFixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline) {
        ReferenceGrayboxLayout.Point marker = new ReferenceGrayboxLayout.Point(anchor.getX() + 12, anchor.getZ() + 12);
        return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), baseline.stateRevision(), baseline.bounds(), baseline.cells(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ReferenceGrayboxSnapshot.Bioform("bioform:raid:raider", 701, "raider",
                                new ReferenceGrayboxLayout.Point(anchor.getX() + 14, anchor.getZ() + 11), "engaging", false, "bioform.raider"),
                        new ReferenceGrayboxSnapshot.Bioform("bioform:raid:breaker", 701, "breaker",
                                new ReferenceGrayboxLayout.Point(anchor.getX() + 15, anchor.getZ() + 13), "engaging", false, "bioform.breaker")),
                List.of(new ReferenceGrayboxSnapshot.Resident("resident:raid:guard", 1, "guard", "worker", "operation", 47,
                                "healthy", "assault", new ReferenceGrayboxLayout.Point(anchor.getX() + 9, anchor.getZ() + 11), "resident.guard"),
                        new ReferenceGrayboxSnapshot.Resident("resident:raid:engineer", 1, "engineer", "worker", "operation", 47,
                                "healthy", "support", new ReferenceGrayboxLayout.Point(anchor.getX() + 9, anchor.getZ() + 13), "resident.engineer")),
                List.of(), List.of(), List.of(new ReferenceGrayboxSnapshot.Activity("operation:47", "operation", "raid", "engaging", marker,
                2.0d, 0.5d, false, "activity.operation.engaging")), List.of(), List.of(), List.of(), List.of(), List.of());
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
