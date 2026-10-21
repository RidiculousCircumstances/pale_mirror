package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.storage.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3OperationStorageGameTests {
    private FrontierV3OperationStorageGameTests() { }
    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void actualPlayerFileCoSavesReceiptButNeverAcknowledgesUnsavedCursor(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel(); var port = FrontierV3OperationPlayers.current(player);
        var operation = FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.PLAYER_GROUND_PICKUP, new SubjectId("test:player-save"),
                "pickup", "bread", Set.of(port.identity())), 1);
        player.getInventory().setItem(0, new ItemStack(Items.BREAD, 3));
        FrontierV3NativeOperationMarkers.stamp(port.persistent(), operation.nativeReceipt(port.identity()));
        var root = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."), "operation-player-");
        try (var access = net.minecraft.world.level.storage.LevelStorageSource.createDefault(root).createAccess("save-test")) {
            var storage = new net.minecraft.world.level.storage.PlayerDataStorage(access, net.minecraft.util.datafix.DataFixers.getDataFixer());
            var target = storage.getPlayerDir().toPath().resolve(player.getStringUUID() + ".dat");
            storage.save(player);
            var saved = FrontierV3OperationPlayers.durableImage(target);
            helper.assertTrue(FrontierV3OperationPlayers.receipts(player.getUUID(), saved)
                    .equals(List.of(operation.nativeReceipt(port.identity()))), "actual player save retains its native receipt");
            helper.assertTrue(!saved.getList("Inventory", 10).isEmpty(), "receipt is saved alongside real inventory");
            player.containerMenu.setCarried(player.getInventory().removeItemNoUpdate(0));
            storage.save(player);
            helper.assertTrue(FrontierV3OperationPlayers.receipts(player.getUUID(), FrontierV3OperationPlayers.durableImage(target)).isEmpty(),
                    "vanilla cursor is not a saved inventory slot; save must withhold receipt");
            helper.assertTrue(FrontierV3NativeOperationMarkers.contains(port.persistent(), operation.nativeReceipt(port.identity())),
                    "withholding snapshot acknowledgement must not erase the live obligation");
            player.getInventory().setItem(0, player.containerMenu.getCarried()); player.containerMenu.setCarried(ItemStack.EMPTY);
            storage.save(player);
            helper.assertTrue(FrontierV3OperationPlayers.receipts(player.getUUID(), FrontierV3OperationPlayers.durableImage(target))
                    .equals(List.of(operation.nativeReceipt(port.identity()))), "normal inventory save can now acknowledge the same operation");
        }
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-shared-storage", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void disjointNativeEffectsShareForcesAndInterruptedBatchCannotReplay(GameTestHelper helper) {
        var level = helper.getLevel(); var ledger = FrontierV3OperationLedger.get(level); ledger.persist(level);
        var points = List.of(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(1, 2, 1)));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var requests = new ArrayList<FrontierV3MinecraftOperation.Request>();
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i); level.setBlockAndUpdate(point, Blocks.STONE.defaultBlockState());
            var port = FrontierV3OperationChunks.current(level, point);
            var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.FIELD_PROJECTION,
                    new SubjectId("test:batch"), "step-" + i, "stone-to-air", Set.of(port.identity()));
            requests.add(new FrontierV3MinecraftOperation.Request(declaration, List.of(port),
                    () -> level.getBlockState(point).is(Blocks.STONE),
                    () -> { calls.incrementAndGet(); return level.setBlockAndUpdate(point, Blocks.AIR.defaultBlockState()); },
                    () -> level.getBlockState(point).isAir()));
        }
        long before = forcedGroups(ledger);
        var applied = FrontierV3MinecraftOperation.applyBatch(level, requests);
        helper.assertTrue(applied.stream().allMatch(FrontierV3MinecraftOperation.Result::complete) && calls.get() == 2,
                "all disjoint real effects apply once");
        helper.assertTrue(forcedGroups(ledger) - before == 2, "one prepare and one applied append for the whole batch");
        helper.assertTrue(FrontierV3MinecraftOperation.applyBatch(level, requests).stream().allMatch(result ->
                result.status() == FrontierV3MinecraftOperation.Status.OBSERVED) && calls.get() == 2, "native receipts prohibit repeat mutations");
        var failures = new ArrayList<FrontierV3MinecraftOperation.Request>();
        for (int i = 0; i < requests.size(); i++) {
            var prior = requests.get(i); int index = i;
            level.setBlockAndUpdate(points.get(i), Blocks.STONE.defaultBlockState());
            var declaration = new FrontierV3OperationReceipt.Declaration(prior.declaration().protocol(), prior.declaration().owner(),
                    "interrupted-" + i, "stone-to-air", prior.declaration().participants());
            failures.add(new FrontierV3MinecraftOperation.Request(declaration, prior.participants(), prior.before(), () -> {
                if (index == 1) throw new IllegalStateException("injected mid-batch failure");
                return prior.mutation().getAsBoolean();
            }, prior.after()));
        }
        boolean failed = false;
        try { FrontierV3MinecraftOperation.applyBatch(level, failures); }
        catch (IllegalStateException expected) { failed = expected.getMessage().equals("injected mid-batch failure"); }
        helper.assertTrue(failed, "the failed effect is visible, never silently committed");
        var recovered = FrontierV3MinecraftOperation.applyBatch(level, failures);
        helper.assertTrue(recovered.getFirst().status() == FrontierV3MinecraftOperation.Status.OBSERVED
                && recovered.getLast().status() == FrontierV3MinecraftOperation.Status.AMBIGUOUS && calls.get() == 3,
                "partial batch retains separate outcomes and never retries an interrupted effect");
        helper.succeed();
    }
    private static long forcedGroups(FrontierV3OperationLedger ledger) {
        return com.google.gson.JsonParser.parseString(ledger.journalDiagnostic()).getAsJsonObject().get("forcedGroups").getAsLong();
    }
    @GameTest(batch = "pm-frontier-v3-shared-storage", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void lastGroundItemReceiptSurvivesNativeEmptyEntityColumnWrite(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var chunk = new net.minecraft.world.level.ChunkPos(helper.absolutePos(new BlockPos(1, 1, 1)));
        var bag = new net.minecraft.nbt.CompoundTag();
        var participant = new FrontierV3OperationReceipt.Participant(FrontierV3OperationReceipt.Medium.ENTITY_COLUMN,
                Long.toString(chunk.toLong()), FrontierV3NativeOperationMarkers.lifetime(bag));
        var operation = FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(
                FrontierV3OperationReceipt.Protocol.SHIPMENT_TRANSFER, new SubjectId("test:ground-pickup"), "pickup", "removed-last-item", Set.of(participant)), 1);
        FrontierV3NativeOperationMarkers.stamp(bag, operation.nativeReceipt(participant));
        var source = new net.minecraft.nbt.CompoundTag();
        source.putIntArray("Position", new int[]{chunk.x, chunk.z});
        source.put("pm_v3_column_operations", bag);
        FrontierV3OperationEntityColumns.loaded(level, chunk, source);
        // Exercise actual EntityStorage including its empty-column cache, in a separate
        // test-owned region directory. Never overwrite the running world's entity file.
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."), "operation-column-");
        var regions = new SimpleRegionStorage(new RegionStorageInfo("operation-test", level.dimension(), "entities"), directory,
                net.minecraft.util.datafix.DataFixers.getDataFixer(), true, net.minecraft.util.datafix.DataFixTypes.ENTITY_CHUNK);
        try (var storage = new EntityStorage(regions, level, Runnable::run)) {
            var empty = new net.minecraft.world.level.entity.ChunkEntities<net.minecraft.world.entity.Entity>(chunk, List.of());
            storage.storeEntities(empty); regions.synchronize(true).join();
            var image = regions.read(chunk).join().orElseThrow();
            helper.assertTrue(image.getList("Entities", 10).isEmpty(), "last item is actually gone");
            helper.assertTrue(FrontierV3OperationEntityColumns.receipts(chunk, image).equals(List.of(operation.nativeReceipt(participant))),
                    "empty native column retains exact pickup receipt");
            storage.storeEntities(empty); regions.synchronize(true).join();
            helper.assertTrue(FrontierV3OperationEntityColumns.receipts(chunk, regions.read(chunk).join().orElseThrow())
                    .equals(List.of(operation.nativeReceipt(participant))), "vanilla empty-column cache must not erase receipt");
            var pig = Objects.requireNonNull(net.minecraft.world.entity.EntityType.PIG.create(level));
            var position = helper.absolutePos(new BlockPos(1, 1, 1)); pig.moveTo(position.getX() + .5, position.getY(), position.getZ() + .5);
            helper.assertTrue(level.addFreshEntity(pig), "test entity admitted");
            var body = FrontierV3OperationEntityParticipant.nativeObject(pig);
            var bodyOperation = FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(
                    FrontierV3OperationReceipt.Protocol.MEAL_CONSUME, new SubjectId("test:removed-body"), "consume", "meal-consumed", Set.of(body.identity())), 2);
            FrontierV3NativeOperationMarkers.stamp(body.persistent(), bodyOperation.nativeReceipt(body.identity()));
            pig.discard();
            storage.storeEntities(empty); regions.synchronize(true).join();
            helper.assertTrue(FrontierV3OperationEntityColumns.receipts(chunk, regions.read(chunk).join().orElseThrow())
                    .contains(bodyOperation.nativeReceipt(body.identity())), "destroyed entity receipt is saved with its witnessed native absence");
        }
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-shared-storage", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void nativeChunkRoundTripRetainsBlockAndContainerReceiptsWithTheirPhysicalState(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = helper.absolutePos(new BlockPos(1, 1, 1));
        var chestPosition = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(chestPosition, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(chestPosition);
        var terrain = FrontierV3OperationChunks.current(level, block);
        var container = FrontierV3OperationBlockEntityParticipant.current(chest);
        var operation = FrontierV3OperationReceipt.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.EXTRACTION_BLOCK,
                new SubjectId("test:operation"), "mine", "retained-output", Set.of(terrain.identity(), container.identity())), 1);
        level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 9));
        for (var participant : List.of(terrain, container)) {
            FrontierV3NativeOperationMarkers.stamp(participant.persistent(), operation.nativeReceipt(participant.identity()));
            participant.dirty().run();
        }
        var chunk = Objects.requireNonNull(level.getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4), "loaded test template");
        var saved = ChunkSerializer.write(level, chunk);
        helper.assertTrue(new HashSet<>(FrontierV3OperationTerrainSaves.receipts(chunk.getPos(), saved)).equals(
                Set.of(operation.nativeReceipt(terrain.identity()), operation.nativeReceipt(container.identity()))),
                "both participants must be in the actual native chunk image");
        var restored = ChunkSerializer.read(level, level.getPoiManager(), new RegionStorageInfo("operation-test", level.dimension(), "chunk"),
                chunk.getPos(), saved);
        // ChunkMap normally performs this before handing the full chunk to gameplay.
        // The serializer deliberately defers block-entity loading to that lifecycle step.
        var full = ((net.minecraft.world.level.chunk.ImposterProtoChunk) restored).getWrapped();
        full.runPostLoad();
        helper.assertTrue(restored.getBlockState(block).isAir(), "block consequence shares the saved receipt");
        var writtenAgain = ChunkSerializer.write(level, full);
        helper.assertTrue(new HashSet<>(FrontierV3OperationTerrainSaves.receipts(chunk.getPos(), writtenAgain)).equals(
                Set.of(operation.nativeReceipt(terrain.identity()), operation.nativeReceipt(container.identity()))),
                "native deserialization/promotion must not lose either marker");
        var restoredChest = (ChestBlockEntity) full.getBlockEntity(chestPosition);
        helper.assertTrue(restoredChest != null && restoredChest.getItem(0).is(Items.COBBLESTONE)
                && restoredChest.getItem(0).getCount() == 9, "container resources survive beside their receipt");
        level.setBlockAndUpdate(chestPosition, Blocks.AIR.defaultBlockState());
        var destroyed = ChunkSerializer.write(level, chunk);
        helper.assertTrue(FrontierV3OperationTerrainSaves.receipts(chunk.getPos(), destroyed)
                .contains(operation.nativeReceipt(container.identity())), "destruction preserves the exact old container receipt in its chunk");
        var afterDestruction = (net.minecraft.world.level.chunk.ImposterProtoChunk) ChunkSerializer.read(level, level.getPoiManager(),
                new RegionStorageInfo("operation-test", level.dimension(), "chunk"), chunk.getPos(), destroyed);
        afterDestruction.getWrapped().runPostLoad();
        helper.assertTrue(afterDestruction.getWrapped().getBlockEntity(chestPosition) == null, "recovery does not recreate the player's destroyed container");
        helper.assertTrue(FrontierV3OperationTerrainSaves.receipts(chunk.getPos(), ChunkSerializer.write(level, afterDestruction.getWrapped()))
                .contains(operation.nativeReceipt(container.identity())), "removal evidence survives native read/write");
        helper.succeed();
    }
}
