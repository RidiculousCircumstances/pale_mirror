package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayStage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical ownership and exact-stack postconditions for the first v3 executor. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3CargoHandoffGameTests {
    private FrontierV3CargoHandoffGameTests() { }

    @GameTest(batch = "pm-frontier-v3-cargo-ownership", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void cargoReceiverClaimsOnlyItsFreshChestAndBindsExactStackIdentity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos receiver = helper.absolutePos(new BlockPos(0, 8, 0));
        level.setBlock(receiver.below(), Blocks.STONE.defaultBlockState(), 3);
        SubjectId container = new SubjectId("container:frontier-v3-game-test-store");
        ChestBlockEntity chest = FrontierV3CargoHandoffExecutor.ownedChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(receiver, container));
        helper.assertTrue(chest != null, "a supported air cell may receive the executor's fresh receiver chest");
        helper.assertValueEqual(chest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY), container.value(),
                "the fresh chest must carry its canonical receiver identity");

        ExactItemStack item = new ExactItemStack(new SubjectId("item:frontier-v3-game-test-bread"), "minecraft:bread", 7,
                new InventoryCustody.ContainerSlot(container, 0));
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(item));
        helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(0), item),
                "confirmation requires the exact materialized item identity, not merely its kind and count");
        chest.getItem(0).setCount(6);
        helper.assertFalse(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(0), item),
                "a changed physical stack must not confirm canonical cargo delivery");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-cargo-conflict", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void cargoReceiverNeverClaimsAnExistingPlayerChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos receiver = helper.absolutePos(new BlockPos(4, 8, 0));
        level.setBlock(receiver.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(receiver, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity playerChest = (ChestBlockEntity) level.getBlockEntity(receiver);
        helper.assertTrue(playerChest != null, "the test must create an unowned chest");

        ChestBlockEntity claimed = FrontierV3CargoHandoffExecutor.ownedChest(level,
                new FrontierV3CargoHandoffExecutor.StoreTarget(receiver, new SubjectId("container:frontier-v3-game-test-store")));
        helper.assertTrue(claimed == null, "an existing unowned chest is a conflict, even when it is empty");
        helper.assertTrue(playerChest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY).isBlank(),
                "the executor must leave a player/world chest unmodified");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-cargo-recovery", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void recoveryInspectionNeverRecreatesAMissingOwnedChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos receiver = helper.absolutePos(new BlockPos(8, 8, 0));
        level.setBlock(receiver.below(), Blocks.STONE.defaultBlockState(), 3);
        SubjectId container = new SubjectId("container:frontier-v3-recovery-store");
        var target = new FrontierV3CargoHandoffExecutor.StoreTarget(receiver, container);
        helper.assertTrue(FrontierV3CargoHandoffExecutor.claimFreshChest(level, target) != null,
                "the pre-crash executor may create one fresh owned chest after durable preparation");
        level.setBlock(receiver, Blocks.AIR.defaultBlockState(), 3);

        helper.assertTrue(FrontierV3CargoHandoffExecutor.activeChest(level, target) == null,
                "recovery inspection must report the missing owned chest rather than recreating it");
        helper.assertTrue(level.getBlockState(receiver).isAir(),
                "the missing surface remains world-owned conflict evidence until an explicit domain resolution");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-container-surface", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void containerSurfaceProjectsCanonicalSlotsOnlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:game-test-container"), 91L));
        SubjectId container = new SubjectId("container:1-depot");
        BlockPos target = helper.absolutePos(new BlockPos(12, 8, 0));
        level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, target, container);

        helper.assertTrue(chest != null, "a prepared exact depot may claim one fresh supported chest");
        helper.assertTrue(FrontierV3ContainerSurfaceExecutor.writeCanonicalSlots(chest, state, container),
                "initial materialization must write every canonical slot with its exact item identity");
        ExactItemStack wheat = state.inventory().itemAt(container, 0).orElseThrow();
        helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(0), wheat),
                "the initial depot stack must retain its canonical item tag");
        helper.assertTrue(chest.getItem(1).isEmpty(), "unowned canonical slots stay physically empty");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-container-recovery", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void alteredPreparedSurfaceIsConflictEvidenceNotRepairWork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:game-test-conflict"), 91L));
        SubjectId container = new SubjectId("container:1-depot");
        BlockPos target = helper.absolutePos(new BlockPos(16, 8, 0));
        level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, target, container);
        helper.assertTrue(chest != null && FrontierV3ContainerSurfaceExecutor.writeCanonicalSlots(chest, state, container),
                "the test needs an owned prepared surface with exact initial contents");
        chest.getItem(0).setCount(1);

        helper.assertFalse(FrontierV3ContainerSurfaceExecutor.matchesCanonicalSlots(chest, state, container),
                "recovery must detect altered exact contents instead of accepting an approximate stack");
        helper.assertValueEqual(chest.getItem(0).getCount(), 1,
                "inspection must leave player/world alteration untouched for visible conflict resolution");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-graybox-provenance", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void grayboxProvenanceDoesNotForgetAConflict(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(20, 8, 0));
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(helper.getLevel());
        ledger.applied(position, "structure:test", "DEPOT", "FOUNDATION"); ledger.conflict(position);
        helper.assertTrue(ledger.claim(position).conflicted(), "a changed applied cell remains conflict evidence, not an invitation to rewrite it");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-graybox-projection", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void grayboxProjectsOnlyAFreshSupportedCellWithSemanticProvenance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(24, 8, 0));
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        GrayboxCell cell = grayboxCell(position, "structure:graybox-fresh", GrayboxMaterial.DEPOT, GrayboxSemanticPart.FOUNDATION);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);

        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, cell), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "an empty supported cell receives exactly its declared graybox block");
        helper.assertTrue(level.getBlockState(position).is(Blocks.YELLOW_CONCRETE), "the depot palette is visibly and deterministically yellow");
        FrontierV3GrayboxLedger.Claim claim = ledger.claim(position);
        helper.assertTrue(claim != null && claim.owner().equals("structure:graybox-fresh")
                        && claim.semanticPart().equals("FOUNDATION") && !claim.conflicted(),
                "the successful block carries durable owner, material and semantic-part provenance");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-graybox-projection", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void grayboxRecordsForeignObstructionWithoutOverwritingIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(28, 8, 0));
        level.setBlock(position, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        GrayboxCell cell = grayboxCell(position, "structure:graybox-obstructed", GrayboxMaterial.HALL, GrayboxSemanticPart.WALL);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);

        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, cell), FrontierV3GrayboxExecutor.ProjectionResult.CONFLICT,
                "a pre-existing world block is a durable obstruction, not materializer input");
        helper.assertTrue(level.getBlockState(position).is(Blocks.DIAMOND_BLOCK), "the foreign block remains physically untouched");
        helper.assertTrue(ledger.claim(position).conflicted(), "the obstruction is remembered so future passes cannot adopt it");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-graybox-projection", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void grayboxDriftBecomesTerminalConflictInsteadOfTemplateRepair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(32, 8, 0));
        GrayboxCell cell = grayboxCell(position, "structure:graybox-drift", GrayboxMaterial.HOUSING, GrayboxSemanticPart.WALL);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, cell), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "the test begins from an owned cell");
        level.setBlock(position, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);

        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, cell), FrontierV3GrayboxExecutor.ProjectionResult.CONFLICT,
                "changed ownership never grants repair authority");
        helper.assertTrue(level.getBlockState(position).is(Blocks.DIAMOND_BLOCK), "the materializer does not restore its template over the changed block");
        helper.assertTrue(ledger.claim(position).conflicted(), "drift remains terminal provenance for later domain reconciliation");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-graybox-causality", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ownedStructureBreakBecomesExactTypedDamageBeforeTheWorldMutation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(36, 8, 0));
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        GrayboxCell cell = grayboxCell(position, "structure:1-hall", GrayboxMaterial.HALL, GrayboxSemanticPart.FOUNDATION);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, ledger, cell), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "the causality test begins from a known owned structural cell");

        var observed = FrontierV3GrayboxExecutor.prepareStructureDamage(level, ledger, position, "player:game-test").orElse(null);
        helper.assertTrue(observed != null && observed.structureId().equals(cell.ownerId()) && observed.position().equals(cell.position())
                        && observed.semanticPart() == GrayboxSemanticPart.FOUNDATION,
                "a real break event receives exact owner, coordinate and semantic evidence before block removal");
        helper.assertFalse(ledger.claim(position).conflicted(), "precondition inspection alone cannot retire a claim before durable acceptance");
        helper.assertTrue(level.getBlockState(position).is(Blocks.WHITE_CONCRETE),
                "observation does not itself alter the live block; Minecraft remains the physical executor");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-physical-observation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void explosionObservationPersistsExactBaselineUntilPostImpactReconciliation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos known = helper.absolutePos(new BlockPos(40, 8, 0));
        BlockPos unknown = helper.absolutePos(new BlockPos(41, 8, 0));
        level.setBlock(known.below(), Blocks.STONE.defaultBlockState(), 3);
        FrontierV3GrayboxLedger provenance = FrontierV3GrayboxLedger.get(level);
        GrayboxCell organ = grayboxCell(known, "organ:explosion-test", GrayboxMaterial.HIVE_HEART, GrayboxSemanticPart.HIVE_TISSUE);
        helper.assertValueEqual(FrontierV3GrayboxExecutor.project(level, provenance, organ), FrontierV3GrayboxExecutor.ProjectionResult.APPLIED,
                "the observation begins with an exact owned organ cell");
        level.setBlock(unknown, Blocks.STONE.defaultBlockState(), 3);
        FrontierV3PhysicalObservationLedger observations = FrontierV3PhysicalObservationLedger.get(level);
        helper.assertTrue(observations.captureExternalExplosion(level, level.getGameTime(), java.util.List.of(known, unknown), provenance, position -> true),
                "real blast candidates are retained before their postcondition is known");
        net.minecraft.nbt.CompoundTag serialized = observations.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
        observations = FrontierV3PhysicalObservationLedger.load(serialized, level.registryAccess());
        level.setBlock(known, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(unknown, Blocks.AIR.defaultBlockState(), 3);

        var first = observations.nextReady(level.getGameTime() + 1L).orElseThrow();
        helper.assertTrue(first.candidate().semantic().isPresent()
                        && first.candidate().semantic().orElseThrow().owner().equals("organ:explosion-test"),
                "the owned part keeps exact semantic provenance across a SavedData reload");
        observations.resolve(first);
        var second = observations.nextReady(level.getGameTime() + 1L).orElseThrow();
        helper.assertTrue(second.candidate().semantic().isEmpty(), "an ordinary changed block remains an unknown scar candidate");
        observations.resolve(second);
        helper.assertTrue(observations.nextReady(level.getGameTime() + 1L).isEmpty(), "each post-impact candidate is consumed exactly once");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-infection-overlay", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void infectionOverlayUsesFreshAirThenRetreatsOrPreservesForeignConflict(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos provisional = helper.absolutePos(new BlockPos(48, 8, 0));
        int x = Math.addExact(Math.multiplyExact(Math.floorDiv(provisional.getX() - 1, InfectionCell.BLOCKS), InfectionCell.BLOCKS), 1);
        int z = Math.addExact(Math.multiplyExact(Math.floorDiv(provisional.getZ() - 1, InfectionCell.BLOCKS), InfectionCell.BLOCKS), 1);
        InfectionCell cell = new InfectionCell(Math.floorDiv(x, InfectionCell.BLOCKS), Math.floorDiv(z, InfectionCell.BLOCKS));
        InfectionOverlayCell desired = new InfectionOverlayCell(cell, x, z, InfectionOverlayStage.BLOOM);
        BlockPos marker = new BlockPos(x, provisional.getY(), z);
        level.setBlock(marker, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(marker.below(), Blocks.STONE.defaultBlockState(), 3);
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:game-test-infection"), 91L));
        FrontierV3InfectionOverlayLedger ledger = FrontierV3InfectionOverlayLedger.get(level);

        helper.assertValueEqual(FrontierV3InfectionOverlayExecutor.project(level, ledger, desired, state),
                FrontierV3InfectionOverlayExecutor.ProjectionResult.APPLIED,
                "a sparse infection cell claims only fresh air above the naturally loaded surface");
        marker = BlockPos.of(ledger.claim(cell).position());
        helper.assertTrue(level.getBlockState(marker).is(Blocks.MAGENTA_CARPET),
                "the bloom stage is an obvious foreign graybox surface marker");
        net.minecraft.nbt.CompoundTag serialized = ledger.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
        ledger = FrontierV3InfectionOverlayLedger.load(serialized, level.registryAccess());
        helper.assertValueEqual(FrontierV3InfectionOverlayExecutor.reconcileRetraction(level, ledger,
                        java.util.Map.entry(cell, ledger.claim(cell)), state), FrontierV3InfectionOverlayExecutor.ProjectionResult.RETRACTED,
                "canonical retreat restores only the exact owned air-baseline marker after SavedData reload");
        helper.assertTrue(level.getBlockState(marker).isAir(), "retraction leaves the captured air baseline, not a terrain rewrite");

        helper.assertValueEqual(FrontierV3InfectionOverlayExecutor.project(level, ledger, desired, state),
                FrontierV3InfectionOverlayExecutor.ProjectionResult.APPLIED, "the same cell may return after a clean retreat");
        level.setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3InfectionOverlayExecutor.project(level, ledger, desired, state),
                FrontierV3InfectionOverlayExecutor.ProjectionResult.CONFLICT,
                "a later player/world change is terminal provenance rather than repaint permission");
        helper.assertTrue(level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK), "the materializer never overwrites the foreign replacement");
        helper.succeed();
    }

    private static GrayboxCell grayboxCell(BlockPos position, String owner, GrayboxMaterial material, GrayboxSemanticPart part) {
        return new GrayboxCell(new BlockPosition(position.getX(), position.getY(), position.getZ()), new SubjectId(owner), material, part);
    }

    @GameTest(batch = "pm-frontier-v3-player-custody", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void playerInventoryObservationRequiresTheExactTaggedStack(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        SubjectId container = new SubjectId("container:frontier-v3-player-custody");
        ExactItemStack expected = new ExactItemStack(new SubjectId("item:frontier-v3-player-bread"), "minecraft:bread", 7,
                new InventoryCustody.ContainerSlot(container, 0));
        player.getInventory().setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(expected));
        helper.assertTrue(FrontierV3InventoryObservationExecutor.hasExactItem(player, expected),
                "player custody observation accepts only the exact canonical NBT-tagged stack");
        player.getInventory().getItem(0).setCount(6);
        helper.assertFalse(FrontierV3InventoryObservationExecutor.hasExactItem(player, expected),
                "a changed stack must not be adopted as the canonical player-held item");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-world-carrier", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void worldCarrierKeepsExactItemIdentityAndItsPersistedEntityUuid(GameTestHelper helper) {
        SubjectId container = new SubjectId("container:frontier-v3-world-carrier");
        ExactItemStack expected = new ExactItemStack(new SubjectId("item:frontier-v3-carrier-bread"), "minecraft:bread", 7,
                new InventoryCustody.ContainerSlot(container, 0));
        java.util.UUID carrier = java.util.UUID.fromString("00000000-0000-0000-0000-000000000045");
        net.minecraft.world.item.ItemStack physical = FrontierV3CargoHandoffExecutor.materializedStack(expected);
        FrontierV3CargoHandoffExecutor.bindWorldCarrier(physical, carrier);
        helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(physical, expected),
                "binding a physical carrier preserves the stack's canonical exact identity");
        helper.assertValueEqual(FrontierV3CargoHandoffExecutor.worldCarrierId(physical).orElseThrow(), carrier,
                "the item carries its persisted carrier UUID for later loaded-chunk reconciliation");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-hopper-carrier", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hopperCarrierBindsOnlyItsExactStackAndRetainsUuid(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(52, 8, 0));
        helper.getLevel().setBlock(position, Blocks.HOPPER.defaultBlockState(), 3);
        net.minecraft.world.level.block.entity.HopperBlockEntity hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) helper.getLevel().getBlockEntity(position);
        SubjectId container = new SubjectId("container:frontier-v3-hopper-carrier");
        ExactItemStack expected = new ExactItemStack(new SubjectId("item:frontier-v3-hopper-bread"), "minecraft:bread", 7,
                new InventoryCustody.ContainerSlot(container, 0));
        hopper.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(expected));
        java.util.UUID first = FrontierV3InventoryObservationExecutor.bindHopperCarrier(hopper, 0);
        java.util.UUID second = FrontierV3InventoryObservationExecutor.bindHopperCarrier(hopper, 0);
        helper.assertValueEqual(second, first, "one loaded hopper keeps its stable carrier UUID across repeated observation");
        helper.assertValueEqual(FrontierV3CargoHandoffExecutor.worldCarrierId(hopper.getItem(0)).orElseThrow(), first,
                "the exact stack and the hopper's persistent carrier identity agree");
        hopper.setItem(1, net.minecraft.world.item.Items.DIAMOND.getDefaultInstance());
        helper.assertFalse(FrontierV3CargoHandoffExecutor.exactMatch(hopper.getItem(1), expected),
                "foreign hopper contents are never confused with the canonical exact stack");
        helper.succeed();
    }
}
