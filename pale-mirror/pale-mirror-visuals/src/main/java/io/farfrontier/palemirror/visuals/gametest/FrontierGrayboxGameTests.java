package io.farfrontier.palemirror.visuals.gametest;

import io.farfrontier.palemirror.api.FrontierPhysicalObservation;
import io.farfrontier.palemirror.api.FrontierProjection;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.runtime.AuthoredVisualProvider;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the actual visual SPI boundary, including an unknown-block recovery path. */
@GameTestHolder(PaleMirrorVisualsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierGrayboxGameTests {
    private FrontierGrayboxGameTests() { }

    @GameTest(batch = "pm-frontier-graybox-claims", templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 40)
    public static void loadedFlatGrayboxClaimsOnlyAirThenPublishesPhysicalDeath(GameTestHelper helper) {
        BlockPos center = new BlockPos(8, 64, 8);
        for (int x = -2; x <= 18; x++) for (int z = -2; z <= 18; z++) {
            helper.getLevel().setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(new BlockPos(x, 64, z), Blocks.AIR.defaultBlockState(), 3);
        }
        BlockPos protectedCell = new BlockPos(3, 64, 4);
        helper.getLevel().setBlock(protectedCell, Blocks.DIRT.defaultBlockState(), 3);
        FrontierProjection projection = projection();

        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), projection);
        helper.assertValueEqual(helper.getLevel().getBlockState(protectedCell).getBlock(), Blocks.DIRT,
                "an unknown block must block initial graybox placement rather than be overwritten");

        helper.getLevel().setBlock(protectedCell, Blocks.AIR.defaultBlockState(), 3);
        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), projection);
        helper.assertValueEqual(helper.getLevel().getBlockState(protectedCell).getBlock(), Blocks.LIME_WOOL,
                "once the conflicting foreign block is removed, the same immutable facility plan must recover");
        ArmorStand civic = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(8, 66, 8)).inflate(2),
                value -> value.getPersistentData().getString("pale_mirror_frontier_id").equals("settlement:frontier:settlement:test"))
                .stream().findFirst().orElseThrow();
        helper.assertTrue(civic.getCustomName() != null && civic.getCustomName().getString()
                        .contains("civic=SIEGE threat=850/1000 | reserve=1.5d ration=820/1000"),
                "a graybox settlement must expose its actual siege, threat, reserve and ration rather than a decorative danger colour");
        Villager resident = helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(center).inflate(16),
                value -> value.getPersistentData().getString("pale_mirror_frontier_id").equals("frontier:settlement:test:resident:01"))
                .stream().findFirst().orElseThrow();
        helper.assertTrue(AuthoredVisualProvider.INSTANCE.observeFrontierEntityDeath(helper.getLevel(), resident, "gametest:death"),
                "a current graybox resident must be recognized as a typed physical observation source");
        List<FrontierPhysicalObservation> observations = List.copyOf(AuthoredVisualProvider.INSTANCE.drainFrontierObservations(helper.getLevel()));
        helper.assertValueEqual(observations.size(), 1, "one physical death must produce exactly one queued observation");
        helper.assertValueEqual(observations.getFirst().type(), FrontierPhysicalObservation.Type.RESIDENT_DIED,
                "the visual provider must not decide death semantics beyond publishing the typed fact");
        resident.discard();
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-graybox-assault", templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 40)
    public static void assaultProjectionMovesItsRealZombieParticipant(GameTestHelper helper) {
        for (int x = -2; x <= 22; x++) for (int z = -2; z <= 18; z++) {
            helper.getLevel().setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(new BlockPos(x, 64, z), Blocks.AIR.defaultBlockState(), 3);
        }
        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), assaultProjection(32, "ENGAGING"));
        Zombie before = frontierZombie(helper, new BlockPos(2, 65, 13));
        helper.assertValueEqual(before.blockPosition(), new BlockPos(2, 65, 13),
                "an engaging assault must place its actual bioform at the assault cell rather than leave it at the hive");

        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), assaultProjection(33, "RETURNING"));
        Zombie after = frontierZombie(helper, new BlockPos(18, 65, 13));
        helper.assertValueEqual(after.getUUID(), before.getUUID(),
                "an assault transition must move the same managed zombie identity, not create a visual substitute");
        helper.assertValueEqual(after.blockPosition(), new BlockPos(18, 65, 13),
                "the immutable next projection must visibly move the participant to its new canonical position");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-graybox-harvester", templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 40)
    public static void harvesterProjectionMovesTheSameZombieAndShowsItsCargoPhase(GameTestHelper helper) {
        for (int x = -2; x <= 38; x++) for (int z = -2; z <= 18; z++) {
            helper.getLevel().setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(new BlockPos(x, 64, z), Blocks.AIR.defaultBlockState(), 3);
        }
        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), harvesterProjection(32, "OUTBOUND", 0L));
        Zombie before = harvesterZombie(helper, new BlockPos(2, 65, 13));
        helper.assertValueEqual(before.blockPosition(), new BlockPos(2, 65, 13),
                "an outbound harvest must materialize its actual green Zombie at the canonical forage-operation position");

        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), harvesterProjection(33, "RETURNING", 32L));
        Zombie after = harvesterZombie(helper, new BlockPos(18, 65, 13));
        helper.assertValueEqual(after.getUUID(), before.getUUID(),
                "a return must move the same managed Zombie rather than make a decorative replacement");
        helper.assertValueEqual(after.blockPosition(), new BlockPos(18, 65, 13),
                "the returned carrier position must come from the immutable harvester projection");
        ArmorStand label = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(24, 67, 8)).inflate(2),
                value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                        .equals("harvester:frontier:harvester:harvest-test:01:day:00000006"))
                .stream().findFirst().orElseThrow();
        helper.assertTrue(label.getCustomName() != null && label.getCustomName().getString()
                        .contains("[R] HARVEST | RETURNING | cargo=32 genetic-milli=1 | receiver=digestive_pool"),
                "a tester must see the return phase, payload and receiving organ instead of a generic green Zombie");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-graybox-spores", templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 60)
    public static void sporeCarrierAndPlayerClearableColonyAreBothLegible(GameTestHelper helper) {
        for (int x = -2; x <= 38; x++) for (int z = -2; z <= 18; z++) {
            helper.getLevel().setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(new BlockPos(x, 64, z), Blocks.AIR.defaultBlockState(), 3);
        }
        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), propagationProjection(32, false));
        Zombie carrier = propagationCarrier(helper, new BlockPos(2, 65, 13));
        helper.assertTrue(carrier.getItemBySlot(EquipmentSlot.HEAD).is(Items.MAGENTA_WOOL),
                "a spore carrier must remain the same individually managed Zombie with a distinct visible type colour");
        helper.runAfterDelay(2, () -> {
            ArmorStand runLabel = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(8, 67, 8)).inflate(2),
                    value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                            .equals("propagation-run:frontier:propagation-run:propagation-test:carrier:day:00000006"))
                    .stream().findFirst().orElseThrow();
            helper.assertTrue(runLabel.getCustomName() != null && runLabel.getCustomName().getString()
                            .contains("[S] SPORES | OUTBOUND | 1/3 | target=33,32"),
                    "a tester must see the carrier's phase, progress and exact foothold target before it arrives");
            ArmorStand hiveLabel = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(8, 66, 8)).inflate(2),
                    value -> value.getPersistentData().getString("pale_mirror_frontier_id").equals("hive:frontier:hive:propagation-test"))
                    .stream().findFirst().orElseThrow();
            helper.assertTrue(hiveLabel.getCustomName() != null && hiveLabel.getCustomName().getString()
                            .contains("adapt=ARMORED_CARAPACE:1"),
                    "an acquired adaptation must be visible on the owning hive rather than remain a hidden combat modifier");

            AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), propagationProjection(33, false));
            Zombie advanced = propagationCarrier(helper, new BlockPos(18, 65, 13));
            helper.assertValueEqual(advanced.getUUID(), carrier.getUUID(),
                    "carrier progress must move one stable Zombie identity, not replace it with a decorative marker");
            AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), propagationProjection(33, true));
            helper.runAfterDelay(2, () -> {
                BlockPos colony = new BlockPos(24, 64, 8);
                helper.assertValueEqual(helper.getLevel().getBlockState(colony).getBlock(), Blocks.BLUE_WOOL,
                        "a delivered infection must become a compact blue player-clearable colony rather than an invisible world mutation");
                ArmorStand colonyLabel = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(24, 66, 8)).inflate(2),
                        value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                                .equals("latent-colony:frontier:latent:propagation-test:00000006:day:00000009:x:33:z:32:label"))
                        .stream().findFirst().orElseThrow();
                helper.assertTrue(colonyLabel.getCustomName() != null && colonyLabel.getCustomName().getString()
                                .contains("[L] LATENT | spores=800 | tissue=460 | clearable"),
                        "the colony marker must disclose its live strength and the player's available response");
                helper.assertTrue(AuthoredVisualProvider.INSTANCE.observeFrontierBlockBreak(helper.getLevel(), colony, "gametest:colony-clear"),
                        "breaking the observed blue colony must publish a typed physical fact, not delete state locally");
                List<FrontierPhysicalObservation> observations = List.copyOf(AuthoredVisualProvider.INSTANCE.drainFrontierObservations(helper.getLevel()));
                helper.assertTrue(observations.stream().anyMatch(value -> value.type() == FrontierPhysicalObservation.Type.LATENT_COLONY_CLEARED),
                        "the materializer must route a player break back to the canonical clear command");
                helper.succeed();
            });
        });
    }

    @GameTest(batch = "pm-frontier-graybox-defend", templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 40)
    public static void defendProjectionMovesItsRealVillagerParticipant(GameTestHelper helper) {
        for (int x = -2; x <= 30; x++) for (int z = -2; z <= 18; z++) {
            helper.getLevel().setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(new BlockPos(x, 64, z), Blocks.AIR.defaultBlockState(), 3);
        }
        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), defendProjection(32, "EN_ROUTE"));
        Villager before = frontierVillager(helper, new BlockPos(6, 65, 10));
        helper.assertValueEqual(before.blockPosition(), new BlockPos(6, 65, 10),
                "an en-route response must move its actual guard Villager to the operation position");

        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), defendProjection(33, "ON_STATION"));
        Villager after = frontierVillager(helper, new BlockPos(22, 65, 10));
        helper.assertValueEqual(after.getUUID(), before.getUUID(),
                "a field-operation transition must move the same managed Villager identity, not make a substitute");
        helper.assertValueEqual(after.blockPosition(), new BlockPos(22, 65, 10),
                "the next immutable projection must visibly reposition the defender");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-z-ecology", templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 40)
    public static void digestiveOrganLabelExposesItsCanonicalEcologicalSubstrate(GameTestHelper helper) {
        for (int x = 14; x <= 34; x++) for (int z = -2; z <= 18; z++) {
            helper.getLevel().setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(new BlockPos(x, 64, z), Blocks.AIR.defaultBlockState(), 3);
        }
        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), ecologyProjection());
        helper.runAfterDelay(2, () -> {
            ArmorStand hiveLabel = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(24, 66, 8)).inflate(2),
                    value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                            .equals("hive:frontier:hive:ecology-test"))
                    .stream().findFirst().orElseThrow();
            ArmorStand label = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(24, 70, 4)).inflate(2),
                    value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                            .equals("hive-organ:frontier:hive:ecology-test:organ:digestive_pool:label"))
                    .stream().findFirst().orElseThrow();
            helper.assertTrue(hiveLabel.getCustomName() != null && hiveLabel.getCustomName().getString().contains("brood-signal=640"),
                    "a loaded hive label must expose its actual command signal, not leave a hidden boolean behind the organ cubes");
            helper.assertTrue(label.getCustomName() != null && label.getCustomName().getString().contains("organic=1400")
                            && label.getCustomName().getString().contains("scar=320"),
                    "a loaded digestive cube must show its actual canonical organic mass and scar, not a cosmetic source");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-graybox-morphogenesis", templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 60)
    public static void growingHiveOrganIsReadableWithoutClaimingATemporaryCube(GameTestHelper helper) {
        for (int x = 14; x <= 34; x++) for (int z = -2; z <= 18; z++) {
            helper.getLevel().setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(new BlockPos(x, 64, z), Blocks.AIR.defaultBlockState(), 3);
        }
        FrontierProjection growing = morphogenesisProjection();
        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), growing);
        helper.runAfterDelay(2, () -> {
            BlockPos anchor = new BlockPos(24, 66, 8);
            ArmorStand label = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(anchor).inflate(2),
                    value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                            .equals("morphogenesis:frontier:hive:morph-test:morphogenesis:synapse:day:4:x:33:z:32"))
                    .stream().findFirst().orElseThrow();
            helper.assertTrue(label.getCustomName() != null && label.getCustomName().getString()
                            .contains("[M] SYNAPSE | GROWING | days=3/4"),
                    "a tester must be able to read an in-progress canonical organ build before a cube appears");
            helper.assertValueEqual(helper.getLevel().getBlockState(new BlockPos(24, 64, 8)).getBlock(), Blocks.AIR,
                    "a growing project must not claim a temporary solid cube that could be mistaken for a finished organ");

            AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), emptyProjection());
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(anchor).inflate(2),
                                value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                                        .equals("morphogenesis:frontier:hive:morph-test:morphogenesis:synapse:day:4:x:33:z:32"))
                                .isEmpty(),
                        "a cancelled or completed project label must retire rather than leave a false growing claim behind");
                helper.succeed();
            });
        });
    }

    @GameTest(batch = "pm-frontier-graybox-campaign", templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 60)
    public static void campaignLabelMovesTheSameVillagerAndRetiresAfterFailure(GameTestHelper helper) {
        for (int x = -2; x <= 54; x++) for (int z = -2; z <= 18; z++) {
            helper.getLevel().setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(new BlockPos(x, 64, z), Blocks.AIR.defaultBlockState(), 3);
        }
        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), campaignProjection(32, "ESTABLISH"));
        Villager deployed = frontierCampaignVillager(helper, new BlockPos(4, 65, 13));
        helper.assertValueEqual(deployed.blockPosition(), new BlockPos(4, 65, 13),
                "an established coalition must move its original red-hat Villager to the canonical front");
        ArmorStand label = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(8, 69, 8)).inflate(2),
                value -> value.getPersistentData().getString("pale_mirror_frontier_id").equals("campaign:frontier:campaign:test:01"))
                .stream().findFirst().orElseThrow();
        helper.assertTrue(label.getCustomName() != null && label.getCustomName().getString()
                        .contains("[C] HIVE_CLEARANCE -> test | ESTABLISH | people=2/2 coalition=2 | supply=780/1000 risk=220/1000"),
                "the graybox must expose the coalition, phase and supply decision without a hidden campaign counter");

        AuthoredVisualProvider.INSTANCE.applyFrontierProjection(helper.getLevel(), campaignProjection(34, "FAILED"));
        helper.runAfterDelay(2, () -> {
            Villager returned = frontierCampaignVillager(helper, new BlockPos(2, 65, 4));
            helper.assertValueEqual(returned.getUUID(), deployed.getUUID(),
                    "a failed campaign must return the same Villager identity to its ordinary settlement position");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(8, 69, 8)).inflate(2),
                            value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                                    .equals("campaign:frontier:campaign:test:01")).isEmpty(),
                    "a terminal campaign label must retire instead of falsely claiming an active front");
            helper.succeed();
        });
    }

    private static FrontierProjection projection() {
        return new FrontierProjection("graybox-10", 1L, 0L,
                List.of(new FrontierProjection.Settlement("frontier:settlement:test", "Teststead", "AGRARIAN", 32, 32, 1,
                        Map.of("FOOD", 3L, "ORE", 0L, "WOOD", 0L, "MEDICINE", 0L, "AMMO", 0L, "POWER", 0L), 0L,
                        "SIEGE", 850, 1_500, 820, 4L)),
                List.of(new FrontierProjection.Resident("frontier:settlement:test:resident:01", "frontier:settlement:test", "FARMER", true,
                        "frontier:resident:frontier:settlement:test:resident:01", 0L)),
                List.of(new FrontierProjection.Facility("frontier:settlement:test:facility:farm", "frontier:settlement:test", "FARM", 32, 32,
                        "OPERATIONAL", 0L)),
                List.of(new FrontierProjection.Operation("frontier:settlement:test:operation:farm", "frontier:settlement:test",
                        "frontier:settlement:test:facility:farm", "FARMING", "RUNNING",
                        "frontier:operation:frontier:settlement:test:operation:farm", 0L)),
                List.of(), List.of(), List.of(), List.of());
    }

    private static FrontierProjection assaultProjection(int cellX, String state) {
        String bioformId = "frontier:hive:assault-test:bioform:01";
        return new FrontierProjection("graybox-10", 2L, 6L, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new FrontierProjection.Hive("frontier:hive:assault-test", 32, 32, 54L, "ACTIVE",
                        "frontier:hive:frontier:hive:assault-test", 0L)),
                List.of(), List.of(new FrontierProjection.Bioform(bioformId, "frontier:hive:assault-test", "RAIDER", 0L, 1,
                        true, "frontier:bioform:" + bioformId, 0L)),
                List.of(new FrontierProjection.Assault("frontier:assault:assault-test:day:00000006", "frontier:hive:assault-test",
                        "frontier:settlement:test", "RAID", state, cellX, 32, List.of(bioformId), 6L, 2, 1, -1L,
                        "frontier:assault:frontier:assault:assault-test:day:00000006", 1L)));
    }

    private static FrontierProjection defendProjection(int cellX, String state) {
        String settlementId = "frontier:settlement:field-test";
        String residentId = settlementId + ":resident:01";
        return new FrontierProjection("graybox-10", 3L, 7L,
                List.of(new FrontierProjection.Settlement(settlementId, "Fieldstead", "AGRARIAN", 32, 32, 1,
                        Map.of("FOOD", 3L, "ORE", 0L, "WOOD", 0L, "MEDICINE", 0L, "AMMO", 2L, "POWER", 0L), 0L)),
                List.of(new FrontierProjection.Resident(residentId, settlementId, "GUARD", true,
                        "frontier:resident:" + residentId, 0L)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new FrontierProjection.FieldOperation("frontier:field:field-test:assault_test:day:00000007", settlementId,
                        "frontier:assault:test", "DEFEND", state, cellX, 32, List.of(residentId), 1, 7L, 1, -1L,
                        "frontier:field-operation:frontier:field:field-test:assault_test:day:00000007", 1L)));
    }

    private static FrontierProjection campaignProjection(int cellX, String phase) {
        String settlementId = "frontier:settlement:campaign-test";
        String residentId = settlementId + ":resident:01";
        String campaignId = "frontier:campaign:test:01";
        return new FrontierProjection("graybox-10", 7L, 38L,
                List.of(new FrontierProjection.Settlement(settlementId, "Campaignstead", "AGRARIAN", 32, 32, 1,
                        Map.of("FOOD", 3L, "ORE", 0L, "WOOD", 0L, "MEDICINE", 1L, "AMMO", 2L, "POWER", 0L), 0L)),
                List.of(new FrontierProjection.Resident(residentId, settlementId, "GUARD", true, "frontier:resident:" + residentId, 0L)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(new FrontierProjection.Campaign(campaignId, settlementId, "frontier:hive:test",
                        "frontier:hive:test:organ:core", "HIVE_CLEARANCE", phase, cellX, 32, List.of(settlementId, "frontier:settlement:ally"),
                        List.of(residentId, "frontier:settlement:ally:resident:01"), 2, 36L, 11, 1, 220, 780, phase.equals("FAILED") ? 39L : -1L,
                        "frontier:campaign:" + campaignId, 1L)), Map.of());
    }

    private static FrontierProjection harvesterProjection(int cellX, String state, long cargo) {
        String hiveId = "frontier:hive:harvest-test";
        String bioformId = hiveId + ":bioform:01";
        String runId = "frontier:harvester:harvest-test:01:day:00000006";
        String receiver = hiveId + ":organ:digestive_pool";
        return new FrontierProjection("graybox-10", 31L, 6L, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new FrontierProjection.Hive(hiveId, 32, 32, 54L, "ACTIVE", "frontier:hive:" + hiveId, 0L)),
                List.of(), List.of(new FrontierProjection.Bioform(bioformId, hiveId, "HARVESTER", 0L, 1, true,
                        "frontier:bioform:" + bioformId, 0L)),
                List.of(new FrontierProjection.HarvesterRun(runId, hiveId, hiveId + ":organ:brood_sac", bioformId,
                        33, 32, cellX, 32, receiver, state, 6L, 2, 1, 2, cargo, 1L, -1L,
                        "frontier:harvester:" + runId, 1L)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    private static FrontierProjection propagationProjection(int currentCellX, boolean delivered) {
        String hiveId = "frontier:hive:propagation-test";
        String bioformId = hiveId + ":bioform:carrier";
        String runId = "frontier:propagation-run:propagation-test:carrier:day:00000006";
        String colonyId = "frontier:latent:propagation-test:00000006:day:00000009:x:33:z:32";
        return new FrontierProjection("graybox-10", 32L, delivered ? 9L : 6L, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new FrontierProjection.Hive(hiveId, 32, 32, 54L, 640, "ACTIVE", "frontier:hive:" + hiveId, 0L)),
                List.of(), List.of(new FrontierProjection.Bioform(bioformId, hiveId, "PROPAGULE_CARRIER", 0L, 1, !delivered,
                        "frontier:bioform:" + bioformId, delivered ? 1L : 0L)),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                delivered ? List.of() : List.of(new FrontierProjection.PropagationRun(runId, hiveId, hiveId + ":organ:sporulator", bioformId,
                        33, 32, currentCellX, 32, "OUTBOUND", 6L, 3, 1, null, -1L, "frontier:propagation-run:" + runId, 1L)),
                delivered ? List.of(new FrontierProjection.LatentColony(colonyId, hiveId, hiveId + ":organ:sporulator", 33, 32,
                        9L, 800L, 460L, "frontier:latent-colony:" + colonyId, 1L)) : List.of(),
                Map.of("ARMORED_CARAPACE", 1));
    }

    private static FrontierProjection ecologyProjection() {
        String hiveId = "frontier:hive:ecology-test";
        String organId = hiveId + ":organ:digestive_pool";
        return new FrontierProjection("graybox-10", 4L, 1L, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new FrontierProjection.Hive(hiveId, 33, 32, 70L, 640, "ACTIVE", "frontier:hive:" + hiveId, 0L)),
                List.of(new FrontierProjection.HiveOrgan(organId, hiveId, "DIGESTIVE_POOL", 33, 32, "ALIVE",
                        "frontier:hive-organ:" + organId, 0L)),
                List.of(), List.of(), List.of(),
                List.of(new FrontierProjection.EcologyCell(33, 32, 1_000L, 250L, 150L, 1_100L, 750L, 320L)));
    }

    private static FrontierProjection morphogenesisProjection() {
        return new FrontierProjection("graybox-10", 5L, 4L, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new FrontierProjection.MorphogenesisProject(
                        "frontier:hive:morph-test:morphogenesis:synapse:day:4:x:33:z:32", "frontier:hive:morph-test",
                        "frontier:hive:morph-test:organ:core", "SYNAPSE", 33, 32, "GROWING", 4L, 3, 4,
                        "frontier:morphogenesis:frontier:hive:morph-test:morphogenesis:synapse:day:4:x:33:z:32", 2L)));
    }

    private static FrontierProjection emptyProjection() {
        return new FrontierProjection("graybox-10", 6L, 5L, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static Zombie frontierZombie(GameTestHelper helper, BlockPos expected) {
        return helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(expected).inflate(2),
                value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                        .equals("frontier:hive:assault-test:bioform:01"))
                .stream().findFirst().orElseThrow();
    }

    private static Zombie harvesterZombie(GameTestHelper helper, BlockPos expected) {
        return helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(expected).inflate(2),
                value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                        .equals("frontier:hive:harvest-test:bioform:01"))
                .stream().findFirst().orElseThrow();
    }

    private static Zombie propagationCarrier(GameTestHelper helper, BlockPos expected) {
        return helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(expected).inflate(2),
                value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                        .equals("frontier:hive:propagation-test:bioform:carrier"))
                .stream().findFirst().orElseThrow();
    }

    private static Villager frontierVillager(GameTestHelper helper, BlockPos expected) {
        return helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(expected).inflate(2),
                value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                        .equals("frontier:settlement:field-test:resident:01"))
                .stream().findFirst().orElseThrow();
    }

    private static Villager frontierCampaignVillager(GameTestHelper helper, BlockPos expected) {
        return helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(expected).inflate(2),
                value -> value.getPersistentData().getString("pale_mirror_frontier_id")
                        .equals("frontier:settlement:campaign-test:resident:01")).stream().findFirst().orElseThrow();
    }
}
