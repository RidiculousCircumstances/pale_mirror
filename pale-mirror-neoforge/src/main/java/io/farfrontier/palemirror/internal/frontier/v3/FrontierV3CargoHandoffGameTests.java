package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceTransition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayStage;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

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

        ExactItemStack item = new ExactItemStack(new SubjectId("item:frontier-v3-game-test-bread"), new SubjectId("hive:frontier"), "minecraft:bread", 7,
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

    @GameTest(batch = "pm-frontier-v3-structural-repair", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void semanticRouteRepairUsesTheExactConcreteStackAndRestoresOnlyItsOwnedCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos target = helper.absolutePos(new BlockPos(22, 8, 0)); BlockPos chestPosition = target.east(2);
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPosition);
        ExactItemStack concrete = new ExactItemStack(new SubjectId("item:repair-game-test"), new SubjectId("route:frontier-network"), "minecraft:gray_concrete", 2,
                new InventoryCustody.ContainerSlot(new SubjectId("container:repair-game-test"), 0));
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(concrete));
        GrayboxCell cell = grayboxCell(target, "route:frontier-network", GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level); ledger.applied(target, cell.ownerId().value(), cell.material().name(), cell.semanticPart().name()); ledger.conflict(target);
        helper.assertTrue(FrontierV3StructuralRepairExecutor.applyOne(level, ledger, target, cell, chest, 0, concrete), "an empty owned loss consumes exactly one matching concrete item");
        helper.assertTrue(level.getBlockState(target).is(Blocks.GRAY_CONCRETE) && chest.getItem(0).getCount() == 1 && !ledger.claim(target).conflicted(),
                "the live block, exact stack and provenance claim converge together");
        level.setBlock(target, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3StructuralRepairExecutor.applyOne(level, ledger, target, cell, chest, 0, concrete), "foreign post-loss geometry is never overwritten by repair");
        helper.assertTrue(chest.getItem(0).getCount() == 1 && level.getBlockState(target).is(Blocks.DIAMOND_BLOCK), "rejected repair leaves both the player/world block and supplied stack untouched");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-route-construction", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void inactiveRouteConstructionConsumesOneExactConcreteAndNeverAdoptsWorldGeometry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos target = helper.absolutePos(new BlockPos(23, 8, 0)); BlockPos chestPosition = target.east(2);
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(chestPosition);
        ExactItemStack concrete = new ExactItemStack(new SubjectId("item:construction-game-test"), new SubjectId("route:frontier-network"), "minecraft:gray_concrete", 2,
                new InventoryCustody.ContainerSlot(new SubjectId("container:construction-game-test"), 0));
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(concrete));
        GrayboxCell cell = grayboxCell(target, "route:frontier-network", GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE);
        FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.get(level);
        helper.assertTrue(FrontierV3RouteConstructionExecutor.applyOne(level, ledger, target, cell, chest, 0, concrete),
                "one fresh inactive corridor cell consumes one exact supplied concrete item");
        helper.assertTrue(level.getBlockState(target).is(Blocks.GRAY_CONCRETE) && chest.getItem(0).getCount() == 1 && ledger.claim(target) != null,
                "the exact live block, stack and newly claimed route provenance must converge together");
        BlockPos foreign = target.south(); level.setBlock(foreign, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3RouteConstructionExecutor.applyOne(level, ledger, foreign, grayboxCell(foreign, "route:frontier-network",
                GrayboxMaterial.ROUTE, GrayboxSemanticPart.ROUTE_SURFACE), chest, 0, concrete),
                "construction must never adopt or overwrite a player/world block");
        helper.assertTrue(level.getBlockState(foreign).is(Blocks.DIAMOND_BLOCK) && chest.getItem(0).getCount() == 1,
                "a rejected foreign cell leaves both world geometry and exact maintenance stock untouched");
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
        ExactItemStack expected = new ExactItemStack(new SubjectId("item:frontier-v3-player-bread"), new SubjectId("settlement:1"), "minecraft:bread", 7,
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
        ExactItemStack expected = new ExactItemStack(new SubjectId("item:frontier-v3-carrier-bread"), new SubjectId("settlement:1"), "minecraft:bread", 7,
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
        ExactItemStack expected = new ExactItemStack(new SubjectId("item:frontier-v3-hopper-bread"), new SubjectId("settlement:1"), "minecraft:bread", 7,
                new InventoryCustody.ContainerSlot(container, 0));
        hopper.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(expected));
        FrontierV3HopperCarrierLedger ledger = FrontierV3HopperCarrierLedger.get(helper.getLevel());
        java.util.UUID first = FrontierV3InventoryObservationExecutor.bindHopperCarrier(ledger, hopper, 0).carrierId();
        java.util.UUID second = FrontierV3InventoryObservationExecutor.bindHopperCarrier(ledger, hopper, 0).carrierId();
        helper.assertValueEqual(second, first, "one loaded hopper keeps its stable carrier UUID across repeated observation");
        helper.assertValueEqual(FrontierV3CargoHandoffExecutor.worldCarrierId(hopper.getItem(0)).orElseThrow(), first,
                "the exact stack and the hopper's persistent carrier identity agree");
        net.minecraft.nbt.CompoundTag serialized = ledger.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
        ledger = FrontierV3HopperCarrierLedger.load(serialized, helper.getLevel().registryAccess());
        helper.assertValueEqual(FrontierV3InventoryObservationExecutor.bindHopperCarrier(ledger, hopper, 0).status(),
                FrontierV3InventoryObservationExecutor.HopperCarrierStatus.CURRENT,
                "a reloaded hopper-carrier ledger preserves its one-to-one physical identity");
        hopper.setItem(1, net.minecraft.world.item.Items.DIAMOND.getDefaultInstance());
        helper.assertFalse(FrontierV3CargoHandoffExecutor.exactMatch(hopper.getItem(1), expected),
                "foreign hopper contents are never confused with the canonical exact stack");
        BlockPos copiedPosition = position.east(2); helper.getLevel().setBlock(copiedPosition, Blocks.HOPPER.defaultBlockState(), 3);
        net.minecraft.world.level.block.entity.HopperBlockEntity copied = (net.minecraft.world.level.block.entity.HopperBlockEntity) helper.getLevel().getBlockEntity(copiedPosition);
        copied.getPersistentData().putUUID(FrontierV3InventoryObservationExecutor.HOPPER_CARRIER_ID_KEY, first);
        copied.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(expected));
        helper.assertValueEqual(FrontierV3InventoryObservationExecutor.bindHopperCarrier(ledger, copied, 0).status(),
                FrontierV3InventoryObservationExecutor.HopperCarrierStatus.CONFLICT,
                "a copied hopper carrier UUID at another position is conflict evidence, never adopted identity");
        helper.assertFalse(FrontierV3CargoHandoffExecutor.worldCarrierId(copied.getItem(0)).isPresent(),
                "conflict observation must leave the copied hopper stack unmodified");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-hopper-return", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeChestObservesExactHopperReturnIntoCanonicalCustody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); WorldId world = new WorldId("frontier:hopper-return-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = helper.absolutePos(new BlockPos(56, 8, 0));
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, container);
        helper.assertTrue(chest != null, "the active owned chest fixture must be constructible");
        FrontierV3CommandSubmission.submit(runtime, "hopper-return-prepare", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED));
        FrontierV3CommandSubmission.submit(runtime, "hopper-return-active", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.ACTIVE));
        ExactItemStack expected = state(runtime).inventory().itemAt(container, 0).orElseThrow();
        BlockPos hopperPosition = chestPosition.east(); level.setBlock(hopperPosition, Blocks.HOPPER.defaultBlockState(), 3);
        net.minecraft.world.level.block.entity.HopperBlockEntity hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) level.getBlockEntity(hopperPosition);
        hopper.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(expected));
        FrontierV3HopperCarrierLedger ledger = FrontierV3HopperCarrierLedger.get(level);
        java.util.UUID carrierId = FrontierV3InventoryObservationExecutor.bindHopperCarrier(ledger, hopper, 0).carrierId();
        FrontierV3CommandSubmission.submit(runtime, "hopper-return-outbound", expected.id().value(),
                new io.farfrontier.palemirror.frontier.v3.model.ExactItemCustodyChanged(expected.id(), expected.custody(), new InventoryCustody.WorldCarrier(carrierId)));
        chest.setItem(0, hopper.getItem(0)); hopper.setItem(0, net.minecraft.world.item.ItemStack.EMPTY);

        helper.assertTrue(FrontierV3InventoryObservationExecutor.observeOne(level, runtime, state(runtime), ledger,
                        new FrontierV3InventoryObservationExecutor.StoreChest(chestPosition, container), chest),
                "the active chest must observe the exact tagged hopper stack rather than recreate or approximate it");
        helper.assertValueEqual(state(runtime).inventory().items().get(expected.id()).custody(), new InventoryCustody.ContainerSlot(container, 0),
                "hopper return must durably restore the same canonical exact item to its owned slot");
        helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(0), expected),
                "custody reconciliation must leave the physical returned stack untouched");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-resource-ingress", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeChestAdmitsOneOrdinaryPlayerStackAsOneExactResource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); WorldId world = new WorldId("frontier:resource-ingress-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = helper.absolutePos(new BlockPos(60, 8, 0));
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, container);
        helper.assertTrue(chest != null, "the ingress fixture needs an owned chest");
        FrontierV3CommandSubmission.submit(runtime, "ingress-prepare", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED));
        FrontierV3CommandSubmission.submit(runtime, "ingress-active", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.ACTIVE));
        ExactItemStack wheat = state(runtime).inventory().itemAt(container, 0).orElseThrow();
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(wheat));
        SubjectId pendingId = new SubjectId("item:ingress-game-test-iron");
        net.minecraft.world.item.ItemStack supplied = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 64);
        FrontierV3CargoHandoffExecutor.bindExactItemId(supplied, pendingId);
        FrontierV3CargoHandoffExecutor.markPendingIngress(supplied);
        chest.setItem(4, supplied);

        helper.assertTrue(FrontierV3InventoryObservationExecutor.observeOne(level, runtime, state(runtime), FrontierV3HopperCarrierLedger.get(level),
                        new FrontierV3InventoryObservationExecutor.StoreChest(chestPosition, container), chest),
                "the active chest must observe one ordinary supplied stack");
        ExactItemStack admitted = state(runtime).inventory().itemAt(container, 4).orElseThrow();
        helper.assertValueEqual(admitted.id(), pendingId, "a recovered admission must reuse its already marked physical identity");
        helper.assertValueEqual(admitted.itemKind(), "minecraft:iron_ingot", "the canonical resource preserves its real Minecraft kind");
        helper.assertValueEqual(admitted.count(), 64, "the canonical resource preserves the real stack count");
        helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(4), admitted),
                "the physical supplied stack retains the one durable canonical identity after admission");
        helper.assertFalse(FrontierV3CargoHandoffExecutor.pendingIngress(chest.getItem(4)),
                "a WAL-acknowledged ingress clears its pending recovery marker");
        helper.succeed();
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }
    /** GameTest-only store; filesystem restart behavior is covered by the server-runtime test. */
    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("GameTest does not compact");
        }
    }
}
